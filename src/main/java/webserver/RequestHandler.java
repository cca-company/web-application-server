package webserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import model.User;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
				connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
			BufferedReader bfr = new BufferedReader(new InputStreamReader(in));

			String url = getRequest(bfr);

			DataOutputStream dos = new DataOutputStream(out);

			if(url.equals("/user/create")){
				response302Header(dos, "/index.html");
			}else{
	
	
				byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
				response200Header(dos, body.length);
				responseBody(dos, body);
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
	
	private void response302Header(DataOutputStream dos, String url) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Location: "+url+" \r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private void responseBody(DataOutputStream dos, byte[] body) {
		try {
			dos.write(body, 0, body.length);
			dos.flush();
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	private String getRequest(BufferedReader bfr) throws IOException {
		String line = bfr.readLine();
		String url = getUrl(line);
		
		int contentLength = 0;
		while (!"".equals(line)) {
			if (line == null) {
				break;
			}
			System.out.println(line);
			line = bfr.readLine();
			String[] token = line.split(" ");
			if(token[0].equals("Content-Length:")){
				contentLength = Integer.parseInt(token[1]);
			}
		}
		String content = IOUtils.readData(bfr, contentLength);
		System.out.println(addMember(content).toString());
		System.out.println(content);
		
		
		return url;
	}

	private String getUrl(String line) {
		String[] tokens = line.split(" ");
		String url = tokens[1];
		int index = url.indexOf("?");
		
		if(index != -1){
			String requestPath = url.substring(0, index);
			String params = url.substring(index + 1);
		}
		
		String requestPath = url;

		return requestPath;
	}

	private User addMember(String query) {
		Map<String, String> params = HttpRequestUtils.parseQueryString(query);
		return new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
	}
}
