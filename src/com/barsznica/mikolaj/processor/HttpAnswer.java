package com.barsznica.mikolaj.processor;

public class HttpAnswer
{
    public int httpCode;
    public String json;

    public HttpAnswer(int httpCode, String json)
    {
        this.httpCode = httpCode;
        this.json = json;
    }
}
