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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import db.DataBase;
import model.User;
import util.HttpRequestUtils;
import util.HttpRequestUtils.Pair;
import util.IOUtils;

public class RequestHandler extends Thread {
	private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

	private Socket connection;
	private Map<String,String> headerContents = new HashMap<String,String>();

	public RequestHandler(Socket connectionSocket) {
		this.connection = connectionSocket;
	}

	public void run() {
		log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
				connection.getPort());

		try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {

			// 버퍼리더를 이용해 리퀘스트 헤더 추출 
			BufferedReader bfr = new BufferedReader(new InputStreamReader(in));

			// url 추출 
			getUrl(bfr.readLine());

			parseHeader(bfr);
			
			response(out);
			
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}
		
	private void response(OutputStream out) throws IOException {
		//response 처
		Map<String, String> cookie = HttpRequestUtils.parseCookies(headerContents.get("Cookie"));
		String requestPath = headerContents.get("Request-Path");
		DataOutputStream dos = new DataOutputStream(out);
		String type = headerContents.get("Accept").split(",")[0];
			
		// 요청 url별 처리 
		if (requestPath.equals("/") || requestPath.equals("") ) {
			
			byte[] body = Files.readAllBytes(new File("./webapp/index.html").toPath());
			response200Header(dos, body.length, "", "text/html");
			responseBody(dos, body);
		} 
		//user/list
		else if (requestPath.equals("/user/list.html")){
			if(cookie.get("logined").equals("true")){
				//User/List.html 생성해서 전
				String html = new String(Files.readAllBytes(new File("./webapp/user/list.html").toPath()));
				StringBuilder sb = new StringBuilder(html);
//				
				String userListHtml = "";
				String format = "<tr>"
						+ "<th>%d</th>"
						+ "<td>%s</td>"
						+ "<td>%s</td>"
						+ "<td>%s</td>"
						+ "<td><a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a></td>"
						+ "</tr>";

				int userNum = 1;
				for(User user : DataBase.findAll()){
					userListHtml += String.format(format, userNum, user.getUserId(), user.getName(), user.getEmail());
				}
				
				sb.replace(sb.indexOf("<%userlist%>"), sb.indexOf("<%userlist%>")+"<%userlist%>".length(), userListHtml);
				byte[] body = sb.toString().getBytes();
				
				response200Header(dos, body.length,"", "text/html");
				responseBody(dos, body);
			}else{
				response302Header(dos, "/user/login.html","");
			}
		}
		//새로운 멤버 추가
		else if (requestPath.equals("/user/create")) {
			String content = headerContents.get("Content");
			addMember(content);
			response302Header(dos, "/index.html","");
		} 
		//로그인
		else if(requestPath.equals("/user/login")){

			String content = headerContents.get("Content");
			Map<String, String> loginParams = HttpRequestUtils.parseQueryString(content);
			User user = DataBase.findUserById(loginParams.get("userId"));
			
			if(user != null && user.getPassword().equals(loginParams.get("password"))){
				response302Header(dos, "/index.html","logined=true;");
			}
			else{
				response302Header(dos, "/user/login_failed.html","logined=false;");
			}
		}
		//페이지 파일 읽어서 보내
		else {
			byte[] body = Files.readAllBytes(new File("./webapp" + requestPath).toPath());
			response200Header(dos, body.length,"", type);
			responseBody(dos, body);
		}
	}

	private void parseHeader(BufferedReader bfr) throws IOException {
		String line = bfr.readLine();
		
		//header variable parsing
		while (!"".equals(line)) {
			
			if (line == null) {
				break;
			}
			System.out.println(line);
			Pair headerContent = HttpRequestUtils.parseHeader(line);
			headerContents.put(headerContent.getKey(), headerContent.getValue());

			line = bfr.readLine();
		}
		System.out.println("");
		
		if(headerContents.get("Method").equals("POST")){
			int contentLength = Integer.parseInt(headerContents.get("Content-Length"));
			String content = IOUtils.readData(bfr, contentLength);
			headerContents.put("Content",content);
		}
		
	}

	private void getUrl(String line) {
		String[] tokens = line.split(" ");
		String url = tokens[1];
		int index = url.indexOf("?");
		
		String requestPath = url;
		String params = "";

		// 파라미터가 있을 경우 
		if (index != -1) {
			requestPath = url.substring(0, index);
			params = url.substring(index + 1);
		}

		headerContents.put("Method", tokens[0]);
		headerContents.put("Request-Path", requestPath);
		headerContents.put("Params", params);
	}

//response
	private void response200Header(DataOutputStream dos, int lengthOfBodyContent, String cookieQuery, String type) {
		try {
			dos.writeBytes("HTTP/1.1 200 OK \r\n");
			dos.writeBytes("Content-Type: "+type+";charset=utf-8\r\n");
			dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
			dos.writeBytes("Set-Cookie: "+ cookieQuery + "\r\n");
			dos.writeBytes("\r\n");
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	
	private void response302Header(DataOutputStream dos, String url, String cookieQuery) {
		try {
			dos.writeBytes("HTTP/1.1 302 Found \r\n");
			dos.writeBytes("Location: " + url + " \r\n");
			dos.writeBytes("Set-Cookie: "+ cookieQuery + "\r\n");
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

	private void addMember(String query) {
		Map<String, String> params = HttpRequestUtils.parseQueryString(query);
		User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
		DataBase.addUser(user);
		
	}
	
}
