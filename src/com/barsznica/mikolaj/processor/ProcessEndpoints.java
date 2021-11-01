package com.barsznica.mikolaj.processor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

public class ProcessEndpoints extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    private void error(Element e, String msg, Object... args)
    {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg, args),
                e);
    }

    private int getBodyAnnotationCount(ExecutableElement method)
    {
        int bodyCount = 0;

        for (var parameter : method.getParameters())
        {
            if (parameter.getAnnotation(Body.class) != null)
            {
                bodyCount++;
            }
        }

        return bodyCount;
    }

    private void processBody(Element element)
    {
        if (element.getKind() == ElementKind.METHOD)
        {
            var endpointMethod = element.getAnnotation(Endpoint.class).method();

            if (endpointMethod == HttpMethod.Post || endpointMethod == HttpMethod.Put)
            {
                int bodyCount = getBodyAnnotationCount((ExecutableElement)element);

                if (bodyCount == 0)
                {
                    error(element, "Body annotations doesn't exists", Endpoint.class.getSimpleName());
                }
                else if (bodyCount > 1)
                {
                    error(element, "Too many body annotations", Endpoint.class.getSimpleName());
                }
            }
        }
    }

    private boolean isInSplittedPath(String[] splittedPath, String parameter)
    {
        for (String pathPart : splittedPath)
        {
            if (pathPart.length() > 2)
            {
                String str = pathPart.substring(1, pathPart.length()-1);

                if (str.equals(parameter) && pathPart.charAt(0) == '{' && pathPart.charAt(pathPart.length()-1) == '}')
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void processEndpointPath(Element element)
    {
        if (element.getKind() == ElementKind.METHOD)
        {
            String path = element.getAnnotation(Endpoint.class).path();
            var splittedPath = path.split("/");

            var method = (ExecutableElement) element;

            for (var parameter : method.getParameters())
            {
                if (parameter.getAnnotation(EndpointPath.class) != null)
                {
                    if (!isInSplittedPath(splittedPath, parameter.getAnnotation(EndpointPath.class).parameter()))
                    {
                        error(element, "Wrong endpoint path for EndpointPath annotation", Endpoint.class.getSimpleName());
                    }
                }
            }
        }
    }

    private String getEndpoint(Element element)
    {
        StringBuilder stringBuilder = new StringBuilder();

        if (element.getKind() == ElementKind.METHOD)
        {
            String path = element.getAnnotation(Endpoint.class).path();
            String method = element.getAnnotation(Endpoint.class).method().toString().toUpperCase();
            TypeElement declaringClass = (TypeElement)element.getEnclosingElement();
            String className = declaringClass.getQualifiedName().toString();
            String endpointMethod = element.getSimpleName().toString();

            var executableElement = (ExecutableElement) element;

            StringBuilder parametersBuilder = new StringBuilder();
            boolean firstTime = true;

            for (var parameter : executableElement.getParameters())
            {
                if (parameter.getAnnotation(EndpointPath.class) != null)
                {
                    String parameterName = parameter.getAnnotation(EndpointPath.class).parameter();
                    var typeOfParameter = parameter.asType();

                    if (!firstTime)
                    {
                        parametersBuilder.append(", ");
                    }
                    if (typeOfParameter.getKind() == TypeKind.INT)
                    {
                        parametersBuilder.append("getIntType(\"" + path + "\", splittedUri, \"" + parameterName + "\")");
                    }
                    else
                    {
                        parametersBuilder.append("getStringType(\"" + path + "\", splittedUri, \"" + parameterName + "\")");
                    }
                    firstTime = false;
                }
            }

            String requestData = (method.equals("PUT") || method.equals("POST")) ? "requestData" : "";
            String methodName = className + "." + endpointMethod;
            String parameters = parametersBuilder + (!firstTime ? ", " : "") + requestData;

            stringBuilder.append("if (isPathRight(\"" + path + "\", splittedUri) && requestMethod.equals(\""+method+"\")){\n\thttpAnswer = "+methodName+"(" + parameters + ");\n}");
        }

        return stringBuilder.toString();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annoations, RoundEnvironment env) {
        String packageName = null;
        StringBuilder stringBuilder = new StringBuilder();
        boolean firstTime = true;
        
        for (var element : env.getElementsAnnotatedWith(Endpoint.class))
        {
            if (!firstTime)
            {
                stringBuilder.append("\nelse ");
            }
            processBody(element);
            processEndpointPath(element);
            stringBuilder.append(getEndpoint(element));
            firstTime = false;

            if (packageName == null)
            {
                packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
            }
        }

        if (packageName != null)
        {
            try {
                var file = processingEnv.getFiler().createSourceFile(packageName + "." + "GeneratedEndpoints").openWriter();
                file.write("package \n" + packageName + ";\n" + "import com.barsznica.mikolaj.processor.*;\n" + "import com.sun.net.httpserver.Headers;\n" + "import com.sun.net.httpserver.HttpExchange;\n" + "import com.sun.net.httpserver.HttpHandler;\n" + "import com.sun.net.httpserver.HttpServer;\n" + "import java.io.BufferedReader;\n" + "import java.io.InputStreamReader;\n" + "import java.io.IOException;\n" + "import java.io.OutputStream;\n" + "import java.util.Map;\n" + "import java.util.LinkedHashMap;\n" + "import java.net.InetSocketAddress;\n" + "import java.nio.charset.StandardCharsets;\n" + "\n" + "public class GeneratedEndpoints implements HttpHandler\n" + "{\t\n" + "\tprivate int getIntType(String path, String[] splittedUri, String parameterName)\n" + "\t{\n" + "\t\tpath = path + \"/\";\n" + "\t\tvar splittedPath = path.split(\"/\");\n" + "\t\tparameterName = \"{\" + parameterName + \"}\";\n" + "\t\t\n" + "\t\tfor (var i=0; i<splittedPath.length; i++)\n" + "        {\n" + "            if (splittedPath[i].equals(parameterName))\n" + "            {\n" + "\t\t\t\tint num = 0;\n" + "\t\t\t\ttry {\n" + "\t\t\t\t\tnum = Integer.parseInt(splittedUri[i]);\n" + "\t\t\t\t}\n" + "\t\t\t\tcatch (NumberFormatException ex){\n" + "\t\t\t\t\tex.printStackTrace();\n" + "\t\t\t\t}\n" + "\t\t\t\treturn num;\n" + "            }\n" + "        }\n" + "\t\t\n" + "\t\treturn 0;\n" + "\t}\n" + "\t\n" + "\tprivate String getStringType(String path, String[] splittedUri, String parameterName)\n" + "\t{\n" + "\t\tpath = path + \"/\";\n" + "\t\tvar splittedPath = path.split(\"/\");\n" + "\t\tparameterName = \"{\" + parameterName + \"}\";\n" + "\t\t\n" + "\t\tfor (var i=0; i<splittedPath.length; i++)\n" + "        {\t\t\t\n" + "            if (splittedPath[i].equals(parameterName))\n" + "            {\n" + "\t\t\t\treturn splittedUri[i];\n" + "            }\n" + "        }\n" + "\t\t\n" + "\t\treturn null;\n" + "\t}\n" + "\t\n" + "\tprivate boolean isPathRight(String path, String[] splittedUri)\n" + "    {\n" + "\t\tpath = path + \"/\";\n" + "\t\tvar splittedPath = path.split(\"/\");\n" + "\t\t\n" + "        if (splittedPath.length != splittedUri.length)\n" + "        {\n" + "\t\t\treturn false;\n" + "        }\n" + "\n" + "        for (var i=0; i<splittedPath.length; i++)\n" + "        {\t\n" + "            if (!splittedPath[i].equals(splittedUri[i]) && splittedPath[i].length() > 0 && splittedPath[i].charAt(0) != '{')\n" + "            {\n" + "                return false;\n" + "            }\n" + "        }\n" + "\n" + "        return true;\n" + "    }\n" + "\t\n" + "\t@Override\n" + "\tpublic void handle(HttpExchange t) throws IOException\n" + "\t{\n" + "\t\tString uri = t.getRequestURI().toString();\n" + "\t\tvar splittedUri = uri.split(\"/\");\n" + "\t\tHttpAnswer httpAnswer = null;\n" + "\t\tvar isr = new InputStreamReader(t.getRequestBody(),\"utf-8\");\n" + "\t\tvar br = new BufferedReader(isr);\n" + "\t\tvar buf = new StringBuilder();\n" + "\t\tint b;\n" + "\t\twhile ((b = br.read()) != -1)\n" + "\t\t{\n" + "\t\t\tbuf.append((char) b);\n" + "\t\t}\n" + "\t\tbr.close();\n" + "\t\tisr.close();\n" + "\t\tString requestData = buf.toString();\n" + "\t\tString requestMethod = t.getRequestMethod();\n" + stringBuilder + "\t\t\n" + "\t\tHeaders headers = t.getResponseHeaders();\n" + "\t\tString response;\n" + "\t\theaders.set(\"Content-Type\", String.format(\"application/json; charset=%s\", StandardCharsets.UTF_8));\n" + "\t\t\n" + "\t\tif (httpAnswer == null)\n" + "\t\t{\n" + "\t\t\tresponse = \"{\\\"serverCode\\\": 404, \\\"response\\\": \\\"Invalid request\\\"}\";\n" + "\t\t\tt.sendResponseHeaders(404, response.length());\n" + "\n" + "\t\t}\n" + "\t\telse\n" + "\t\t{\n" + "\t\t\tresponse = \"{\\\"serverCode\\\": \" + httpAnswer.httpCode + \", \\\"response\\\": \\\"\" + httpAnswer.json + \"\\\"}\";\n" + "\t\t\tt.sendResponseHeaders(httpAnswer.httpCode, response.length());\n" + "\t\t}\n" + "\t\tSystem.out.println(\"Processing:\\n\" + \"Uri: \" + uri + \"\\t\\tMethod: \" + requestMethod + \"\\t\\tBody: \" + requestData + \"\\nResponse: \" + response + \"\\n-------------------------\");\n" + "\t\t\n" + "\t\tOutputStream os = t.getResponseBody();\n" + "\t\tos.write(response.getBytes());\n" + "\t\tos.close();\n" + "\t}\n" + "}\n" + "\n");
                file.flush();
                file.close();
            } catch (IOException ex) {
                messager.printMessage(Diagnostic.Kind.ERROR, "error creating file: " + ex);
            }
        }

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        var annotations = new LinkedHashSet<String>();
        annotations.add(Endpoint.class.getCanonicalName());
        annotations.add(EndpointPath.class.getCanonicalName());
        annotations.add(Body.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    public static void main(String[] args)
    {

    }
}