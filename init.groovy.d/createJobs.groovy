import jenkins.model.*
import org.jenkinsci.plugins.workflow.cps.*
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.model.BooleanParameterDefinition

def jenkins = Jenkins.instance
def configDir = new File(jenkins.rootDir, "pipeline-config")

if (!configDir.exists()) {
    println "pipeline-config folder does not exist: ${configDir.absolutePath}"
    return
}

configDir.listFiles().findAll { it.name.endsWith(".cfg") }.each { cfgFile ->
    println "Processing config file: ${cfgFile.name}"

    def configMap = [:]
    cfgFile.eachLine { line ->
        line = line.trim()
        if (!line || line.startsWith("#")) return
        if (line.contains("=")) {
            def (key, value) = line.split("=", 2)
            configMap[key.trim()] = value.trim()
        }
    }

    def pipelineScriptName = configMap.remove("PIPELINE")
    if (!pipelineScriptName) {
        println "No PIPELINE specified in ${cfgFile.name}, skipping..."
        return
    }

    def jobName = cfgFile.name.replace(".cfg", "")    
    println "Processing job ${jobName} using pipeline ${pipelineScriptName}"
    def existingJob = jenkins.getItem(jobName)
    
    if (existingJob) {
        println "Job ${jobName} already exists, updating pipeline script and parameters..."
        // Update the pipeline script
        def pipelineFile = new File(jenkins.rootDir, "pipelines/${pipelineScriptName}")
        if (pipelineFile.exists()) {
            def scriptText = pipelineFile.text
            // Replace default values in the pipeline script with values from config file
            configMap.each { k, v ->
                // Escape backslashes in the value for proper Groovy string replacement
                def escapedValue = v.replace('\\', '\\\\')
                
                // Replace defaultValue: 'old_value' with defaultValue: 'new_value'
                def pattern = /defaultValue:\s*'[^']*'/
                def replacement = "defaultValue: '${escapedValue}'"
                scriptText = scriptText.replaceAll(/(name:\s*'${k}',\s*)${pattern}/, '$1' + replacement)
                
                // Also handle boolean values
                if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
                    def boolPattern = /defaultValue:\s*(true|false)/
                    def boolReplacement = "defaultValue: ${Boolean.parseBoolean(v)}"
                    scriptText = scriptText.replaceAll(/(name:\s*'${k}',\s*)${boolPattern}/, '$1' + boolReplacement)
                }
            }
            existingJob.definition = new CpsFlowDefinition(scriptText, true)
            
            // No need to set job parameters since we're dynamically generating the pipeline script
            
            existingJob.save()
            println "Pipeline script and parameters updated for job ${jobName}"
        } else {
            println "Pipeline script not found: ${pipelineFile.absolutePath}, skipping update..."
        }
        return
    }

    def pipelineFile = new File(jenkins.rootDir, "pipelines/${pipelineScriptName}")
    if (!pipelineFile.exists()) {
        println "Pipeline script not found: ${pipelineFile.absolutePath}, skipping..."
        return
    }

    def scriptText = pipelineFile.text
    // Replace default values in the pipeline script with values from config file
    configMap.each { k, v ->
        // Escape backslashes in the value for proper Groovy string replacement
        def escapedValue = v.replace('\\', '\\\\')
        
        // Replace defaultValue: 'old_value' with defaultValue: 'new_value'
        def pattern = /defaultValue:\s*'[^']*'/
        def replacement = "defaultValue: '${escapedValue}'"
        scriptText = scriptText.replaceAll(/(name:\s*'${k}',\s*)${pattern}/, '$1' + replacement)
        
        // Also handle boolean values
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            def boolPattern = /defaultValue:\s*(true|false)/
            def boolReplacement = "defaultValue: ${Boolean.parseBoolean(v)}"
            scriptText = scriptText.replaceAll(/(name:\s*'${k}',\s*)${boolPattern}/, '$1' + boolReplacement)
        }
    }

    // Create the pipeline job
    def job = jenkins.createProject(org.jenkinsci.plugins.workflow.job.WorkflowJob, jobName)
    job.definition = new CpsFlowDefinition(scriptText, true)

    // No need to set job parameters since we're dynamically generating the pipeline script

    job.save()
    println "Job ${jobName} created with parameters."
}
