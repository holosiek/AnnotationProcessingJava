package com.barsznica.mikolaj.server;

import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;

public class HttpServer
{
    private static HttpHandler getHandler()
    {
        String handlerName = HttpServer.class.getPackageName() + ".GeneratedEndpoints";
        HttpHandler handler = null;

        try
        {
            handler = (HttpHandler)Class.forName(handlerName).getDeclaredConstructor().newInstance();
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e)
        {
            System.out.println("Error related to endpoints:");
            e.printStackTrace();
        }
        return handler;
    }

    public HttpServer(int port, int maxListeners)
    {
        try
        {
            var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), maxListeners);
            server.createContext("/", getHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            System.out.println("Server started and listening at port: " + port);
        }
        catch (IOException e)
        {
            System.out.println("Error related to server:");
            e.printStackTrace();
        }
    }
}
