/*
 * Copyright 2013-2015 RapidMiner GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rapidminer.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.wrapper.Wrapper

/**
 * The main class for the RapidMiner Extension Gradle plugin which is used by RapidMiner extensions.
 *
 * @author Nils Woehler
 *
 */
class RapidMinerExtensionPlugin implements Plugin<Project> {

    private static final String EXTENSION_GROUP = "RapidMiner Extension"

    private static final String RMX = "rmx_"

    private static final String DEFAULT_JAVA_PATH = "src/main/java/";
    public static final String DEFAULT_RESOURCE_PATH = "src/main/resources/";
    private static final String RAPIDMINER_PACKAGE = "com/rapidminer/";
    private static final String RESOURCE_PACKAGE = RAPIDMINER_PACKAGE + "resources/"
    private static final String I18N_PATH = "i18n/"

    private static final String INIT_CLASS_PREFIX = "PluginInit"

    private static final String JAVA_EXTENSION = ".java"
    private static final String XML_EXTENSION = ".xml"
    private static final String PROPERTIES_EXTENSION = ".properties"

    private static final String NOT_FOUND = "\$NOT_FOUND\$"

    def Project project

    @Override
    void apply(Project project) {
        this.project = project

        // create 'extension' project extension
        project.extensions.create("extensionConfig", ExtensionConfiguration)

        configureProject(project);
    }

    /**
     * Configures the project with default gradle plugins and provided configurations.
     */
    void configureProject(Project project) {
        project.configure(project) {

            // For publishing extension to Maven
            apply plugin: 'java'
            apply plugin: 'com.rapidminer.java-publishing.extension'

            // shadowJar is being used to create a shaded extension jar
            apply plugin: 'com.github.johnrengelman.shadow'

            // Add Java basics and code style plugins
            apply plugin: 'com.rapidminer.java-basics'
            apply plugin: 'com.rapidminer.code-quality'

            // Configure extension artifact publications
            project.logger.info "Configuring extension publication by adding 'all' artifacts and fixing artifactId."
            publication {
                artifactId { project.extensionConfig.namespace }
                releases {
                    artifact shadowJar { classifier 'all' }
                }
                snapshots {
                    artifact shadowJar { classifier 'all' }
                }
            }

            // Let compile extend from provided.
            // This ensures that newer versions of compile dependencies do overwrite older versions from provided configuration.
            configurations { compile.extendsFrom project.configurations.provided }

            // add and configure Gradle wrapper task
            def wrapperTask = tasks.create(name: 'wrapper', type: Wrapper)
            wrapperTask.description = "Adds/Updates the Gradle wrapper."
            wrapper { gradleVersion = "${extensionConfig.wrapperVersion}" }

            try {
                // check whether the init class is already present
                checkInitClass(project, project.extensionConfig.resources, name, logger)

                // init class present, create 'installExtension' task
                def installTask = tasks.create(name: 'installExtension', type: Copy, dependsOn: 'shadowJar')
                installTask.group = EXTENSION_GROUP
                installTask.description = "Create a jar bundled with all dependencies and copies the jar" +
                        " to the configured 'extensionFolder'. By default the extension is copied to '~/.RapidMiner/extensions'."

                // configure install task
                installExtension {
                    into getExtensionInstallFolder(project)
                    from shadowJar
                }

                defaultTasks 'installExtension'

                def checkManifestTask = tasks.create(name: 'checkManifestEntries')
                checkManifestTask.description = "Checks whether the extension manifest entries are correct."

                checkManifestEntries {
                    doLast {
                        if (!project.extensionConfig.name) {
                            throw new GradleException('No RapidMiner Extension name defined. Define via \'extensionConfig { name $NAME }\'.')
                        }

                        if (!project.extensionConfig.groupId) {
                            throw new GradleException('No groupdId defined! Define via \'extensionConfig { groupdId $GROUPID }\'. (default: \'com.rapidminer.extension\')')
                        }
                        checkReleaseManifestEntries(project)
                    }
                }
                jar.dependsOn checkManifestEntries
                shadowJar.dependsOn checkManifestEntries

            } catch (e) {

                // Add extension initialization task if Init class could not be found
                def initTask = tasks.create(name: 'initializeExtensionProject', type: ExtensionInitialization)
                initTask.group = EXTENSION_GROUP
                initTask.description = 'Initializes a extension project with all files needed to start the development of a RapidMiner Studio extension.'
                initTask.dependsOn wrapper

                defaultTasks 'initializeExtensionProject'
            }

            // define extension group as lazy GString
            // see http://forums.gradle.org/gradle/topics/how_do_you_delay_configuration_of_a_task_by_a_custom_plugin_using_the_extension_method
            group = "${-> project.extensionConfig.groupId}"

            def createOpTask = tasks.create(name: 'createOperator', type: OperatorCreation)
            createOpTask.group = EXTENSION_GROUP
            createOpTask.description = 'Creates a new operator by adding a new Java class, adding the operator documentation and adding the operator to the operators XML.'

            // Configuring the properties below can only be accomplished after
            // the project extension 'extension' has been configured
            afterEvaluate {

                def rmDep = getRapidMinerDependency(project)
                project.logger.info "Adding RapidMiner Core dependency  (${rmDep})"

                // add RapidMiner and configured extensions as dependencies
                dependencies {
                    provided rmDep
                    extensionConfig.dependencies.extensions.each { ExtensionDependency extDep ->
                        if (project.logger.infoEnabled) {
                            project.logger.info "Adding RapidMiner Extension dependency (${extDep})"
                        }
                        if(extDep.project){
                            provided extDep.project
                        } else {
                            provided group: extDep.group, name: extDep.namespace, version: extDep.version
                        }
                    }
                }

                // add check for manifest entries to avoid generic 'null' error
                assignReleaseManifestEntries(project)

                // configure create extension release task
                jar {
                    // configure manifest
                    manifest {
                        attributes(
                                "Manifest-Version": "1.0",
                                "Implementation-Vendor": project.extensionConfig.vendor ?: '',
                                "Implementation-Title": project.extensionConfig.name ?: '',
                                "Implementation-URL": project.extensionConfig.homepage ?: '',
                                "Implementation-Version": project.version ?: '',
                                "Specification-Title": project.extensionConfig.name ?: '',
                                "Specification-Version": project.version ?: '',
                                "RapidMiner-Version": getRapidMinerVersion(project) ?: '',
                                "RapidMiner-Type": "RapidMiner_Extension",
                                "Plugin-Dependencies": getExtensionDependencies(project) ?: '',

                                // Definition of important files
                                "Extension-ID": RMX + (project.extensionConfig.namespace ?: ''),
                                "Namespace": project.extensionConfig.namespace ?: '',
                                "Initialization-Class": project.extensionConfig.resources.initClass ?: '',
                                "IOObject-Descriptor": project.extensionConfig.resources.objectDefinition ?: '',
                                "Operator-Descriptor": project.extensionConfig.resources.operatorDefinition ?: '',
                                "ParseRule-Descriptor": project.extensionConfig.resources.parseRuleDefinition ?: '',
                                "Group-Descriptor": project.extensionConfig.resources.groupProperties ?: '',
                                "Error-Descriptor": project.extensionConfig.resources.errorDescription ?: '',
                                "UserError-Descriptor": project.extensionConfig.resources.userErrors ?: '',
                                "GUI-Descriptor": project.extensionConfig.resources.guiDescription ?: '',
                                "Settings-Descriptor": project.extensionConfig.resources.settingsDescriptor ?: '',
                                "SettingsStructure-Descriptor": project.extensionConfig.resources.settingsStructureDescriptor ?: ''
                        )
                    }
                }

                // ensure provided dependencies are not compiled into shadowJar
                def firstLevelProvided = project.configurations.provided.getResolvedConfiguration().getFirstLevelModuleDependencies()
                def artifactsToExclude = getResolvedArtifacts(firstLevelProvided)

                artifactsToExclude.each { artifact ->
                    project.logger.info "Excluding ${artifact} from being bundled into the shadow jar."
                    shadowJar {
                        dependencies {
                            exclude(dependency(artifact))
                        }
                    }
                }

            }
        }
    }

    def getExtensionInstallFolder(project) {
        return {
            def installFolderPath = project.extensionConfig.extensionFolder
            if (installFolderPath) {
                // check if specified path is valid
                if (new File(installFolderPath).isDirectory()) {
                    return installFolderPath
                } else {
                    throw new GradleException("The path to the extension installation folder '$installFolderPath' is invalid. " +
                            "Please specify a path to a valid directory or remove the assignment to 'extensionFolder' to use the default path.")
                }
            } else {
                // use default path (~/.RapidMiner/extensions/)
                def defaultPath = "${System.properties['user.home']}/.RapidMiner/extensions/"
                project.logger.info "Using default installation path for 'installExtension' task: $defaultPath"
                return defaultPath
            }
        }
    }


    def getResolvedArtifacts(Set<ResolvedArtifact> artifacts) {
        Set<String> resolvedArtifacts = [] as Set
        artifacts.each {
            // add current artifact
            resolvedArtifacts << "${it.moduleGroup}:${it.moduleName}:${it.moduleVersion}"

            // recursion to add children
            resolvedArtifacts += getResolvedArtifacts(it.children)
        }
        return resolvedArtifacts
    }

    def checkReleaseManifestEntries(Project project) {
        assert project.version
        assert project.extensionConfig.vendor

        def res = project.extensionConfig.resources
        def name = project.extensionConfig.name?.replace(" ", "") ?: ''
        def logger = project.logger

        // Check for Initialization class
        checkInitClass(project, res, name, logger)

        // Check for Operator definitions
        def operatorsResource = checkResourceFile("Operators", XML_EXTENSION, res.operatorDefinition, project, res, name, logger, true, "", "OperatorsDoc")
        def resourceFile = project.file(DEFAULT_RESOURCE_PATH + operatorsResource)
        def docBundle = new XmlSlurper().parse(resourceFile)?.attributes()?.get('docbundle')
        if (!docBundle) {
            throw new GradleException("No docBundle in operators definitions '${operatorsResource}' defined. \nWrong resource file selected? Please define path to Operator definitions manually.\nDefault path to operator definitions: ${RESOURCE_PACKAGE}Operators${name}.xml")
        }

        def docBundleFile = project.file(DEFAULT_RESOURCE_PATH + docBundle + ".xml")
        if (!docBundleFile.exists()) {
            throw new GradleException("DocBundle file defined in operators definitions ('${docBundle}') does not exists.")
        }
    }

    def assignReleaseManifestEntries(Project project) {
        def res = project.extensionConfig.resources
        def name = project.extensionConfig.name?.replace(" ", "") ?: ''
        def logger = project.logger

        res.initClass = getInitClass(project, res, name, logger)
        res.operatorDefinition = getResourceFile("Operators", XML_EXTENSION, res.operatorDefinition, project, res, name, logger, true, "", "OperatorsDoc")
        res.objectDefinition = getResourceFile("ioobjects", XML_EXTENSION, res.objectDefinition, project, res, name, logger)
        res.parseRuleDefinition = getResourceFile("parserules", XML_EXTENSION, res.parseRuleDefinition, project, res, name, logger)
        res.groupProperties = getResourceFile("groups", PROPERTIES_EXTENSION, res.groupProperties, project, res, name, logger)
        res.errorDescription = getResourceFile("Errors", PROPERTIES_EXTENSION, res.errorDescription, project, res, name, logger, false, I18N_PATH)
        res.userErrors = getResourceFile("UserErrorMessage", PROPERTIES_EXTENSION, res.userErrors, project, res, name, logger, false, I18N_PATH)
        res.guiDescription = getResourceFile("GUI", PROPERTIES_EXTENSION, res.guiDescription, project, res, name, logger, false, I18N_PATH)
        res.settingsStructureDescriptor = getResourceFile("settings", XML_EXTENSION, res.settingsStructureDescriptor, project, res, name, logger, false)
        res.settingsDescriptor = getResourceFile("Settings", PROPERTIES_EXTENSION, res.settingsDescriptor, project, res, name, logger, false, I18N_PATH)
    }

    def getInitClass(Project project, res, name, logger) {
        try {
            return checkInitClass(project, res, name, logger)
        } catch (e) {
            return NOT_FOUND
        }
    }

    def static checkInitClass(Project project, res, name, logger) {
        // Check if init class is user defined
        def defaultFileName = DEFAULT_JAVA_PATH + RAPIDMINER_PACKAGE + INIT_CLASS_PREFIX + name + JAVA_EXTENSION
        if (!res.initClass) {
            if (project.file(defaultFileName).exists()) {
                logger.info "Found default init class: '${defaultFileName}'"
                return RAPIDMINER_PACKAGE.replace("/", ".") + INIT_CLASS_PREFIX + name
            } else {
                logger.info("Default init class  '${defaultFileName}' not found. Searching for alternatives in 'src/main/java' ...")

                // Create a file tree with a base directory
                FileTree tree = project.fileTree(dir: DEFAULT_JAVA_PATH, include: '**/*' + JAVA_EXTENSION)

                // Iterate over the contents of a tree
                def initCandidate = null
                tree.find { File file ->
                    if (file.getName().contains(INIT_CLASS_PREFIX)) {
                        logger.info("Found potential init class: ${file.getPath()}")
                        def idx = file.getPath().indexOf(DEFAULT_JAVA_PATH.replace("/", File.separator))
                        initCandidate = file.getPath()
                                .substring(idx + DEFAULT_JAVA_PATH.length())
                                .replace(JAVA_EXTENSION, "")
                                .replace(File.separator, ".")
                        return true // take this one
                    }
                    return false // not found yet
                }

                // Still not found?
                if (initCandidate == null) {
                    throw new GradleException("No init class candidate found!")
                }
                logger.info "Selected init class: '${initCandidate}'"
                return initCandidate
            }
        } else {
            // check if user defined init class exists
            def initClassFile = project.file(DEFAULT_JAVA_PATH + res.initClass.replace(".", File.separator) + ".java")
            if (!initClassFile.exists()) {
                throw new GradleException("Extension init class '${initClassFile}' does not exist! \nDefault path: ${defaultFileName}")
            }
            return res.initClass // use the user-defined one
        }
    }

    def getResourceFile(String resourceName, String suffix, userDefinedResource, Project project, res, name, logger, boolean mandatory = false, String subdirectory = "", String excludedModifier = null) {
        try {
            return checkResourceFile(resourceName, suffix, userDefinedResource, project, res, name, logger, mandatory, subdirectory, excludedModifier)
        } catch (e) {
            logger.info "Could not find resource file for resource '${resourceName}'"
            return NOT_FOUND
        }
    }

    def checkResourceFile(String resourceName, String suffix, userDefinedResource, Project project, res, name, logger, boolean mandatory = false, String subdirectory = "", String excludedModifier = null) {
        // Check if resource file is user defined
        def defaultResourceFile = RESOURCE_PACKAGE + subdirectory + resourceName + name + suffix
        if (!userDefinedResource) {
            if (project.file(DEFAULT_RESOURCE_PATH + defaultResourceFile).exists()) {
                logger.info("Found default " + resourceName + " resource file: '" + defaultResourceFile + "'")
                return defaultResourceFile
            } else {
                logger.info("Default " + resourceName + " resource file '" + defaultResourceFile + "' not found."
                        + " Searching for alternatives in " + DEFAULT_RESOURCE_PATH + "...")
                if(excludedModifier) {
	            	logger.info "Excluding files which contain ${excludedModifier}."
                }

                // Create a file tree with a base directry
                FileTree tree = project.fileTree(dir: DEFAULT_RESOURCE_PATH, include: "**/*${suffix}")

                // Iterate over the contents of a tree
                def resourceCandidate = null
                tree.find { File file ->
                	def containsExcludedString = (excludedModifier == null || !file.getName().contains(excludedModifier))
    				if(excludedModifier) {
                		logger.info "${file} contains excluded modifier: ${excludedModifier}" 
    				}
                    if (file.getName().contains(resourceName) && containsExcludedString) {
                        logger.info("Found potential " + resourceName + " resource file: " + file.getPath())
                        def idx = file.getPath().indexOf(DEFAULT_RESOURCE_PATH.replace("/", File.separator))
                        resourceCandidate = file.getPath()
                                .substring(idx + DEFAULT_RESOURCE_PATH.length())
                                .replace(File.separator, "/")
                        return true // take this one
                    }
                    return false // not found yet
                }

                // Still not found?
                if (resourceCandidate == null) {
                    if (mandatory) {
                        throw new GradleException("Mandatory '${resourceName}' resource file is missing. No candidate found!")
                    } else {
                        resourceCandidate = ""
                    }
                    logger.info("No optional resource file for '${resourceName}' found. Skipping...")
                } else {
                    logger.info("Selected resource file for '${resourceName}': ${resourceCandidate}")
                }
                return resourceCandidate
            }
        } else {
            def fileName = DEFAULT_RESOURCE_PATH + userDefinedResource
            def resourceFile = project.file(fileName)
            if (!resourceFile.exists()) {
                throw new GradleException("Resource file for resource '${resourceName}' does not exist! \nDefault path: ${defaultResourceFile}")
            }
            def idx = resourceFile.getPath().indexOf(DEFAULT_RESOURCE_PATH.replace("/", File.separator))
            return resourceFile.getPath()
                    .substring(idx + DEFAULT_RESOURCE_PATH.length())
                    .replace(File.separator, "/")
        }
    }

    def getExtensionDependencies(Project project) {
        String deps = ""
        project.extensionConfig.dependencies.extensions.eachWithIndex { ExtensionDependency extDep, i ->
            if (i != 0) {
                deps += "; "
            }
            deps += "${RMX}${getExtensionNamespace(extDep)}[${getExtensionVersion(extDep)}]"
        }
        return deps
    }

    def getExtensionVersion(ExtensionDependency dep) {
        if(dep.version && dep.project){
            throw new GradleException("Either specify a version or a project. Both is not allowed!")
        }
        if(dep.version){
            return dep.version
        } else if(dep.project){
            return dep.project.version
        } else {
            throw new GradleException("Missing extension version. Please specify either a version or project dependency for ${dep.namespace}.")
        }
    }

    def getExtensionNamespace(ExtensionDependency dep) {
        if(dep.namespace && dep.project){
            throw new GradleException("Either specify a namespace or a project. Both is not allowed!")
        }
        if(dep.namespace){
            return dep.namespace
        } else if(dep.project){
            if(!dep.project.extensionConfig){
                throw new GradleException("The project ${dep.project.name} doesn't look like an extension project. Please make sure the Extension Gradle plugin is applied.")
            }
            return dep.project.extensionConfig.namespace
        } else {
            throw new GradleException("Missing extension namespace. Please specify either a namespace or project dependency for ${dep.namespace}.")
        }
    }

    def getRapidMinerDependency(Project project) {
        if (project.extensionConfig.dependencies.project) {
            return project.extensionConfig.dependencies.project
        } else {
            def version = getRapidMinerVersion(project)
            return "com.rapidminer.studio:rapidminer-studio-core:${version}"
        }
    }

    def getRapidMinerVersion(Project project) {
        if (project.extensionConfig.dependencies.project) {
            return project.extensionConfig.dependencies.project.version
        } else {
            assert project.extensionConfig.dependencies.rapidminer
            return project.extensionConfig.dependencies.rapidminer
        }
    }

}
