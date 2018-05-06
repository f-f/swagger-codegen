package io.swagger.codegen.languages;

import io.swagger.codegen.*;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import io.swagger.models.properties.*;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

public class ClojureClientCodegen extends DefaultCodegen implements CodegenConfig {
    private static final String PROJECT_NAME = "projectName";
    private static final String PROJECT_DESCRIPTION = "projectDescription";
    private static final String PROJECT_VERSION = "projectVersion";
    private static final String PROJECT_URL = "projectUrl";
    private static final String PROJECT_LICENSE_NAME = "projectLicenseName";
    private static final String PROJECT_LICENSE_URL = "projectLicenseUrl";
    private static final String BASE_NAMESPACE = "baseNamespace";

    protected String projectName;
    protected String projectDescription;
    protected String projectVersion;
    protected String baseNamespace;

    protected String sourceFolder = "src";
    protected String modulePath = null;

    public ClojureClientCodegen() {
        super();
        outputFolder = "generated-code" + File.separator + "clojure";
        apiTemplateFiles.put("api.mustache", ".clj");
        embeddedTemplateDir = templateDir = "clojure";

        cliOptions.add(new CliOption(PROJECT_NAME,
                "name of the project (Default: generated from info.title or \"swagger-clj-client\")"));
        cliOptions.add(new CliOption(PROJECT_DESCRIPTION,
                "description of the project (Default: using info.description or \"Client library of <projectName>\")"));
        cliOptions.add(new CliOption(PROJECT_VERSION,
                "version of the project (Default: using info.version or \"1.0.0\")"));
        cliOptions.add(new CliOption(PROJECT_URL,
                "URL of the project (Default: using info.contact.url or not included in project.clj)"));
        cliOptions.add(new CliOption(PROJECT_LICENSE_NAME,
                "name of the license the project uses (Default: using info.license.name or not included in project.clj)"));
        cliOptions.add(new CliOption(PROJECT_LICENSE_URL,
                "URL of the license the project uses (Default: using info.license.url or not included in project.clj)"));
        cliOptions.add(new CliOption(BASE_NAMESPACE,
                "the base/top namespace (Default: generated from projectName)"));

        typeMapping.clear();

        // We have specs for most of the types:
        typeMapping.put("integer", "int?");
        typeMapping.put("long", "int?");
        typeMapping.put("short", "int?");
        typeMapping.put("number", "float?");
        typeMapping.put("float", "float?");
        typeMapping.put("double", "float?");
        typeMapping.put("array", "list?");
        typeMapping.put("map", "map?");
        typeMapping.put("boolean", "boolean?");
        typeMapping.put("string", "string?");
        typeMapping.put("char", "char?");
        typeMapping.put("date", "inst?");
        typeMapping.put("DateTime", "inst?");
        typeMapping.put("UUID", "uuid?");

        // But some type mappings are not really worth/meaningful to check for:
        typeMapping.put("object", "any?"); // Like, everything is an object.
        typeMapping.put("file", "any?");   // We don't really have specs for files,
        typeMapping.put("binary", "any?"); // nor binary.
        // And while there is a way to easily check if something is a bytearray,
        // (https://stackoverflow.com/questions/14796964/), it's not possible
        // to conform it yet, so we leave it as is.
        typeMapping.put("ByteArray", "any?");
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "clojure";
    }

    @Override
    public String getHelp() {
        return "Generates a Clojure client library.";
    }

    @Override
    public String getTypeDeclaration(Property p) {
        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p;
            Property inner = ap.getItems();
            return "(s/coll-of " + getTypeDeclaration(inner) + ")";
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p;
            Property inner = mp.getAdditionalProperties();
            return "(s/map-of string? " + getTypeDeclaration(inner) + ")";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public String getSwaggerType(Property p) {
        String swaggerType = super.getSwaggerType(p);

        if (typeMapping.containsKey(swaggerType)) {
            return typeMapping.get(swaggerType);
        } else if (swaggerType == "object") {
            return "A.Value"; // TODO
        } else {
            return toModelName(swaggerType) + "-spec";
        }
    }

    @Override
    public String toModelName(String name) {
        return dashize(name);
    }

    @Override
    public String toVarName(String name) {
        name = name.replaceAll("[^a-zA-Z0-9_-]+", ""); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.
        name = dashize(name);
        return name;
    }

    @Override
    public void preprocessSwagger(Swagger swagger) {
        super.preprocessSwagger(swagger);

        if (additionalProperties.containsKey(PROJECT_NAME)) {
            projectName = ((String) additionalProperties.get(PROJECT_NAME));
        }
        if (additionalProperties.containsKey(PROJECT_DESCRIPTION)) {
            projectDescription = ((String) additionalProperties.get(PROJECT_DESCRIPTION));
        }
        if (additionalProperties.containsKey(PROJECT_VERSION)) {
            projectVersion = ((String) additionalProperties.get(PROJECT_VERSION));
        }
        if (additionalProperties.containsKey(BASE_NAMESPACE)) {
            baseNamespace = ((String) additionalProperties.get(BASE_NAMESPACE));
        }

        if (swagger.getInfo() != null) {
            Info info = swagger.getInfo();
            if (projectName == null &&  info.getTitle() != null) {
                // when projectName is not specified, generate it from info.title
                projectName = dashize(info.getTitle());
            }
            if (projectVersion == null) {
                // when projectVersion is not specified, use info.version
                projectVersion = info.getVersion();
            }
            if (projectDescription == null) {
                // when projectDescription is not specified, use info.description
                projectDescription = info.getDescription();
            }

            if (info.getContact() != null) {
                Contact contact = info.getContact();
                if (additionalProperties.get(PROJECT_URL) == null) {
                    additionalProperties.put(PROJECT_URL, contact.getUrl());
                }
            }
            if (info.getLicense() != null) {
                License license = info.getLicense();
                if (additionalProperties.get(PROJECT_LICENSE_NAME) == null) {
                    additionalProperties.put(PROJECT_LICENSE_NAME, license.getName());
                }
                if (additionalProperties.get(PROJECT_LICENSE_URL) == null) {
                    additionalProperties.put(PROJECT_LICENSE_URL, license.getUrl());
                }
            }
        }

        // default values
        if (projectName == null) {
            projectName = "swagger-clj-client";
        }
        if (projectVersion == null) {
            projectVersion = "1.0.0";
        }
        if (projectDescription == null) {
            projectDescription = "Client library of " + projectName;
        }
        if (baseNamespace == null) {
            baseNamespace = dashize(projectName);
        }
        apiPackage = baseNamespace + ".api";

        additionalProperties.put(PROJECT_NAME, projectName);
        additionalProperties.put(PROJECT_DESCRIPTION, escapeText(projectDescription));
        additionalProperties.put(PROJECT_VERSION, projectVersion);
        additionalProperties.put(BASE_NAMESPACE, baseNamespace);
        additionalProperties.put(CodegenConstants.API_PACKAGE, apiPackage);

        final String baseNamespaceFolder = sourceFolder + File.separator + namespaceToFolder(baseNamespace);
        supportingFiles.add(new SupportingFile("project.mustache", "", "project.clj"));
        supportingFiles.add(new SupportingFile("core.mustache", baseNamespaceFolder, "core.clj"));
        supportingFiles.add(new SupportingFile( "specs.mustache", baseNamespaceFolder, "specs.clj"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
    }

    @Override
    public String sanitizeTag(String tag) {
        return tag.replaceAll("[^a-zA-Z_]+", "_");
    }

    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + sourceFolder + File.separator + namespaceToFolder(apiPackage);
    }

    @Override
    public String toOperationId(String operationId) {
        // throw exception if method name is empty
        if (StringUtils.isEmpty(operationId)) {
            throw new RuntimeException("Empty method/operation name (operationId) not allowed");
        }

        return dashize(sanitizeName(operationId));
    }

    @Override
    public String toApiFilename(String name) {
        return underscore(toApiName(name));
    }

    @Override
    public String toApiName(String name) {
        return dashize(name);
    }

    @Override
    public String toParamName(String name) {
        return toVarName(name);
    }

    @Override
    public String escapeText(String input) {
        if (input == null) {
            return null;
        }
        return input.trim().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> operations) {
        Map<String, Object> objs = (Map<String, Object>) operations.get("operations");
        List<CodegenOperation> ops = (List<CodegenOperation>) objs.get("operation");
        for (CodegenOperation op : ops) {
            // Convert httpMethod to lower case, e.g. "get", "post"
            op.httpMethod = op.httpMethod.toLowerCase();
        }
        return operations;
    }

    @SuppressWarnings("static-method")
    protected String namespaceToFolder(String ns) {
        return ns.replace(".", File.separator).replace("-", "_");
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // ref: https://clojurebridge.github.io/community-docs/docs/clojure/comment/
        return input.replace("(comment", "(_comment");
    }
}
