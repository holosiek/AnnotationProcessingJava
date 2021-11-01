package 
//Podmien na package
;
import com.barsznica.mikolaj.processor.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.LinkedHashMap;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class GeneratedEndpoints implements HttpHandler
{	
	private int getIntType(String path, String[] splittedUri, String parameterName)
	{
		path = path + "/";
		var splittedPath = path.split("/");
		parameterName = "{" + parameterName + "}";
		
		for (var i=0; i<splittedPath.length; i++)
        {
            if (splittedPath[i].equals(parameterName))
            {
				int num = 0;
				try {
					num = Integer.parseInt(splittedUri[i]);
				}
				catch (NumberFormatException ex){
					ex.printStackTrace();
				}
				return num;
            }
        }
		
		return 0;
	}
	
	private String getStringType(String path, String[] splittedUri, String parameterName)
	{
		path = path + "/";
		var splittedPath = path.split("/");
		parameterName = "{" + parameterName + "}";
		
		for (var i=0; i<splittedPath.length; i++)
        {			
            if (splittedPath[i].equals(parameterName))
            {
				return splittedUri[i];
            }
        }
		
		return null;
	}
	
	private boolean isPathRight(String path, String[] splittedUri)
    {
		path = path + "/";
		var splittedPath = path.split("/");
		
        if (splittedPath.length != splittedUri.length)
        {
			return false;
        }

        for (var i=0; i<splittedPath.length; i++)
        {	
            if (!splittedPath[i].equals(splittedUri[i]) && splittedPath[i].length() > 0 && splittedPath[i].charAt(0) != '{')
            {
                return false;
            }
        }

        return true;
    }
	
	@Override
	public void handle(HttpExchange t) throws IOException
	{
		String uri = t.getRequestURI().toString();
		var splittedUri = uri.split("/");
		HttpAnswer httpAnswer = null;
		var isr = new InputStreamReader(t.getRequestBody(),"utf-8");
		var br = new BufferedReader(isr);
		var buf = new StringBuilder();
		int b;
		while ((b = br.read()) != -1)
		{
			buf.append((char) b);
		}
		br.close();
		isr.close();
		String requestData = buf.toString();
		String requestMethod = t.getRequestMethod();
		//Podmianka na stringBuilder
		
		Headers headers = t.getResponseHeaders();
		String response;
		headers.set("Content-Type", String.format("application/json; charset=%s", StandardCharsets.UTF_8));
		
		if (httpAnswer == null)
		{
			response = "{\"serverCode\": 404, \"response\": \"Invalid request\"}";
			t.sendResponseHeaders(404, response.length());

		}
		else
		{
			response = "{\"serverCode\": " + httpAnswer.httpCode + ", \"response\": \"" + httpAnswer.json + "\"}";
			t.sendResponseHeaders(httpAnswer.httpCode, response.length());
		}
		System.out.println("Processing:\n" + "Uri: " + uri + "\t\tMethod: " + requestMethod + "\t\tBody: " + requestData + "\nResponse: " + response + "\n-------------------------");
		
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}
}
