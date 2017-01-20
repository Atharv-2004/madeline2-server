package madeline2.server

import org.apache.commons.lang.StringEscapeUtils
import org.apache.log4j.Logger

/**
 * Service for generating genograms
 * @author Philip Wu
 */
class GenogramService {

    Logger logger = Logger.getLogger(GenogramService.class)
    def grailsApplication


    public File generatePedigreeFile(def request) {

        // Save the file to disk to temporary folder
        String filename = request.getRemoteAddr()
        if ( ! filename) {
            filename = ''
        }
        // Guarantee uniqueness across multiple concurrent requests
        filename += "_"+System.currentTimeMillis()+"_"+Thread.currentThread().id
        File pedigreeFile = File.createTempFile(filename,".madeline2")

        return pedigreeFile

    }

    /**
     * Generate the genogram file from the given pedigree file
     * @param pedigreeFile
     * @return
     */
    public File generateGenogram(File pedigreeFile, List<String> labels = null) {
        logger.info("file contents:\n"+pedigreeFile.text)

        // clean the file
        pedigreeFile = cleanPedigreeFile(pedigreeFile)

        // Combine the labels as a command line argument
        String cmdLabels = ""
        if (labels) {
            cmdLabels = labels.join(" ")
        }

        // Execulte command line to generate genogram
//        String madeline2Command = "madeline2"
//        if (cmdLabels) {
//            madeline2Command += ' --Labels \''+cmdLabels+'\''
//        }
//        madeline2Command += " --font-size 9 --nolabeltruncation --color --outputprefix "+pedigreeFile.name +" "+pedigreeFile.absolutePath

        List<String> cmdList = ['madeline2']
        if (cmdLabels) {
            cmdList << '--Labels'
            cmdList << cmdLabels
        }
        cmdList << "--font-size"
        cmdList << "9"
        cmdList << "--nolabeltruncation"
        cmdList << "--color"
        //cmdList << "--scalable"
        cmdList << "--outputprefix"
        cmdList << pedigreeFile.name
        cmdList << pedigreeFile.absolutePath


        File workingDir = new File(System.getProperty('java.io.tmpdir'))
        def (resp, err) = CliExec.execCommand(cmdList, workingDir, null, true, true)
        logger.info("resp="+resp)
        logger.info("err="+err)

        // Write the image to the response
        String genogramFilename = pedigreeFile.name + '.svg'
        File genogramFile = new File(workingDir.absolutePath+"/"+genogramFilename)

        // Error handling in case madeline2 fails to generate the genogram, most likely due to badly formatted input
        if (! genogramFile.exists()) {
            throw new Exception(err)
        }

        return genogramFile
    }

    /**
     * Madeline2 requires that the number of bytes in each row of the data block be the same for all rows.
     * We may need to pad some rows with extra spaces.
     * @param pedigreeFile
     * @return
     */
    public File cleanPedigreeFile(File pedigreeFile) {
        boolean inDataBlock = false

        // Encode to HTML for SVG display
        String fileContent = pedigreeFile.text
        fileContent = fileContent.encodeAsHTML()

        // First calculate the maximum number of bytes for each row
        int maxRowBytes = 0
        int index = 0
        fileContent.eachLine { line ->
            // Look for data block
            if (! inDataBlock) {
                if (index > 0 && line.trim() == '') {
                    inDataBlock = true
                }
            } else {
                maxRowBytes = Math.max(line.bytes.length, maxRowBytes)
            }
            index++
        }

        logger.info("maxRowBytes="+maxRowBytes)

        // Now pad the rows
        inDataBlock = false
        List lines = []
        fileContent.eachLine { line ->
            // Look for data block
            if (! inDataBlock) {
                if (index > 0 && line.trim() == '') {
                    inDataBlock = true
                }
            } else {
                // in data block
                //line = line.encodeAsHTML().padRight(maxRowBytes)
                line = line.padRight(maxRowBytes)
            }

            lines << line
        }

        // Combine all the lines back into a string separated by newline character
        String strFile = lines.join('\n')
        logger.info("strFile="+strFile)


        // Overwrite the original file
        pedigreeFile.newWriter().withWriter { w ->
            w << strFile
        }

        return pedigreeFile
    }
}
