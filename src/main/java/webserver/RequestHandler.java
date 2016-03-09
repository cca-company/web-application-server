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
			
			// 버퍼리더를 이용해 url 추출 
			BufferedReader bfr = new BufferedReader(new InputStreamReader(in));
			String line = bfr.readLine();
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

			int contentLength = 0;
			Map<String, String> cookie = new HashMap<String, String>();
			String type = "";
			while (!"".equals(line)) {
				
				if (line == null) {
					break;
				}
				System.out.println(line);
				line = bfr.readLine();
				String[] token = line.split(" ");
				if (token[0].equals("Content-Length:")) {
					contentLength = Integer.parseInt(token[1]);
				}else if(token[0].equals("Cookie:")){
					cookie = HttpRequestUtils.parseCookies(token[1]);
				}else if(token[0].equals("Accept:")){
					String[] acceptToken = token[1].split(",");
					type = acceptToken[0];
				}
					
				
			}
			System.out.println("\n");
			String content = IOUtils.readData(bfr, contentLength);

			DataOutputStream dos = new DataOutputStream(out);

			// 요청 url별 처리 
			if (requestPath.equals("/") || requestPath.equals("/") ) {
				byte[] body = Files.readAllBytes(new File("./webapp/index.html").toPath());
				response200Header(dos, body.length, "", "text/html");
				responseBody(dos, body);
			} 
			else if (requestPath.equals("/user/list.html")){
				if(cookie.get("logined").equals("true")){
					String html = new String(Files.readAllBytes(new File("./webapp/user/list.html").toPath()));
					StringBuilder sb = new StringBuilder(html);
					String userListHtml = "";
					int userNum = 1;
					for(User user : DataBase.findAll()){
						userListHtml+="<tr>";
						userListHtml+="<th scope=\"row\">";
						userListHtml+=userNum;
						userListHtml+="</th>";
						userListHtml+="<td>";
						userListHtml+=user.getUserId();
						userListHtml+="</td>";
						userListHtml+="<td>";
						userListHtml+=user.getName();
						userListHtml+="</td>";
						userListHtml+="<td>";
						userListHtml+=user.getEmail();
						userListHtml+="</td>";
						userListHtml+="<td>";
						userListHtml+="<a href=\"#\" class=\"btn btn-success\" role=\"button\">수정</a>";
						userListHtml+="</td>";
						userListHtml+="</tr>";
						++userNum;						
					}
					sb.replace(sb.indexOf("<%userlist%>"), sb.indexOf("<%userlist%>")+"<%userlist%>".length(), userListHtml);
					byte[] body = sb.toString().getBytes();
					
					response200Header(dos, body.length,"", "text/html");
					responseBody(dos, body);
				}else{
					response302Header(dos, "/user/login.html","");
				}
			}
			else if (requestPath.equals("/user/create")) {
				addMember(content);
				response302Header(dos, "/index.html","");
			} 
			else if(requestPath.equals("/user/login")){
				Map<String, String> loginParams = HttpRequestUtils.parseQueryString(content);
				User user = DataBase.findUserById(loginParams.get("userId"));
				System.out.println(loginParams.get("password") + " " + user.getPassword());
				if(user != null && user.getPassword().equals(loginParams.get("password"))){
					response302Header(dos, "/index.html","logined=true;");
				}
				else{
					response302Header(dos, "/index.html","logined=false;");
				}
			}
			else {
				byte[] body = Files.readAllBytes(new File("./webapp" + requestPath).toPath());
				response200Header(dos, body.length,"", type);
				responseBody(dos, body);
			}
		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

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
