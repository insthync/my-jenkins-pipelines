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
    println "Creating job ${jobName} using pipeline ${pipelineScriptName}"
    def existingJob = jenkins.getItem(jobName)
    /*
    if (existingJob) {
        println "Job ${jobName} already exists, skipping..."
        return
    }
    */
    if (existingJob) {
        println "Job ${jobName} already exists, deleting..."
        existingJob.delete()
    }

    def pipelineFile = new File(jenkins.rootDir, "pipelines/${pipelineScriptName}")
    if (!pipelineFile.exists()) {
        println "Pipeline script not found: ${pipelineFile.absolutePath}, skipping..."
        return
    }

    def scriptText = pipelineFile.text
    // Replace placeholders inside the script if you still want defaults updated
    configMap.each { k, v ->
        scriptText = scriptText.replace("\${${k}}", v)
    }

    // Create the pipeline job
    def job = jenkins.createProject(org.jenkinsci.plugins.workflow.job.WorkflowJob, jobName)
    job.definition = new CpsFlowDefinition(scriptText, true)

    // Set job parameters from config
    def paramsList = []
    configMap.each { k, v ->
        def description = "Configured from ${cfgFile.name}" // default description
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            paramsList << new BooleanParameterDefinition(k, Boolean.parseBoolean(v), description)
        } else {
            paramsList << new StringParameterDefinition(k, v, description)
        }
    }
    if (paramsList) {
        job.addProperty(new ParametersDefinitionProperty(paramsList))
    }

    job.save()
    println "Job ${jobName} created with parameters."
}
