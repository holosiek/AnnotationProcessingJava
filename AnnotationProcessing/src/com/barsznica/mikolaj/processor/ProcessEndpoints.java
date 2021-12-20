package com.barsznica.mikolaj.processor;

import com.barsznica.mikolaj.commonap.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ProcessEndpoints extends AbstractProcessor {
    private Elements elementUtils;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        messager = processingEnv.getMessager();
    }

    private void logError(Element e, String msg, Object... args)
    {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    private void logWarning(Element e, String msg, Object... args)
    {
        messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args), e);
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

    private boolean isBodyNeeded(HttpMethod method)
    {
        return method == HttpMethod.Post || method == HttpMethod.Put;
    }

    private void validateBodyAnnotations(ExecutableElement element)
    {
        int bodyCount = getBodyAnnotationCount(element);

        if (bodyCount == 0)
        {
            logError(element, "Body annotations doesn't exists", Endpoint.class.getSimpleName());
        }
        else if (bodyCount > 1)
        {
            logError(element, "Too many body annotations", Endpoint.class.getSimpleName());
        }
    }

    private void processBody(Element element)
    {
        if (element.getKind() == ElementKind.METHOD)
        {
            var endpointMethod = element.getAnnotation(Endpoint.class).method();

            if (isBodyNeeded(endpointMethod))
            {
                validateBodyAnnotations((ExecutableElement)element);
            }
        }
    }

    private boolean isEnclosedInBrackets(String str)
    {
        return str.charAt(0) == '{' && str.charAt(str.length()-1) == '}';
    }

    private boolean isInSplittedPath(String[] splittedPath, String parameter)
    {
        for (String pathPart : splittedPath)
        {
            if (pathPart.length() > 2)
            {
                String str = pathPart.substring(1, pathPart.length()-1);

                if (str.equals(parameter) && isEnclosedInBrackets(pathPart))
                {
                    return true;
                }
            }
        }

        return false;
    }

    private void validateEndpointPathAnnotation(Element element, VariableElement parameter)
    {
        String path = element.getAnnotation(Endpoint.class).path();
        String parameterPath = parameter.getAnnotation(EndpointPath.class).parameter();
        var splittedPath = path.split("/");

        if (!isInSplittedPath(splittedPath, parameterPath))
        {
            logError(element, "Wrong endpoint path for EndpointPath annotation", Endpoint.class.getSimpleName());
        }

    }

    private void processEndpointPath(Element element)
    {
        if (element.getKind() == ElementKind.METHOD)
        {
            var method = (ExecutableElement) element;

            for (var parameter : method.getParameters())
            {
                if (parameter.getAnnotation(EndpointPath.class) != null)
                {
                    validateEndpointPathAnnotation(element, parameter);
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
            String openingBracket = !method.equals("GET") ? "synchronized(" + normalizeClassName(className) + "){" : "";
            String closingBracket = !method.equals("GET") ? "}" : "";
            String parameters = parametersBuilder + (!firstTime && !requestData.equals("") ? ", " : "") + requestData;

            stringBuilder.append("if (isPathRight(\"" + path + "\", splittedUri) && requestMethod.equals(\""+method+"\")){\n"+openingBracket+"\thttpAnswer = "+normalizeClassName(className)+"."+endpointMethod+"(" + parameters + ");\n" + closingBracket + "}");
        }

        return stringBuilder.toString();
    }

    private String getTemplateData() throws IOException
    {
        var templateInputStream = getClass().getClassLoader().getResourceAsStream("template.txt");

        if (templateInputStream == null)
        {
            messager.printMessage(Diagnostic.Kind.ERROR, "Cannot find template.txt file");
            return null;
        }
        return new String(templateInputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void generateFile(String packageName, String stringBuilder)
    {
        if (packageName != null)
        {
            try
            {
                String sourceFilename = packageName + "." + "GeneratedEndpoints";
                String content = getTemplateData();

                if (content != null)
                {
                    content = content.replaceAll("##package##", packageName);
                    content = content.replaceAll("##endpoints##", stringBuilder);
                    var file = processingEnv.getFiler().createSourceFile(sourceFilename).openWriter();
                    file.write(content.toString());
                    file.flush();
                    file.close();
                }
            }
            catch (IOException ex)
            {
                messager.printMessage(Diagnostic.Kind.ERROR, "Error creating file: " + ex);
            }
        }
    }

    public String getElementClass(Element element)
    {
        return ((TypeElement)element.getEnclosingElement()).getQualifiedName().toString();
    }

    private String normalizeClassName(String className)
    {
        return className.replaceAll("\\.", "");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annoations, RoundEnvironment env)
    {
        var endpointHandlerBuilder = new StringBuilder();
        var classesBuilder = new StringBuilder();
        var classes = new ArrayList<String>();
        String packageName = null;
        boolean firstTime = true;
        
        for (var element : env.getElementsAnnotatedWith(Endpoint.class))
        {
            String elementClassName = getElementClass(element);

            if (!firstTime)
            {
                endpointHandlerBuilder.append("\nelse ");
            }

            if (!classes.contains(elementClassName))
            {
                classes.add(elementClassName);
                classesBuilder.append("var ").append(normalizeClassName(elementClassName)).append(" = new ").append(elementClassName).append("();\n");
            }

            processBody(element);
            processEndpointPath(element);

            if (packageName == null)
            {
                packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
            }
            firstTime = false;
            endpointHandlerBuilder.append(getEndpoint(element));
        }

        endpointHandlerBuilder.insert(0, classesBuilder);
        generateFile(packageName, endpointHandlerBuilder.toString());

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes()
    {
        var annotations = new LinkedHashSet<String>();
        annotations.add(Endpoint.class.getCanonicalName());
        annotations.add(EndpointPath.class.getCanonicalName());
        annotations.add(Body.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion()
    {
        return SourceVersion.latestSupported();
    }

    public static void main(String[] args)
    {
    }
}