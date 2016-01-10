package marshall.backup.messenger.webclient;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.jdom2.input.SAXBuilder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for fetching messages from FB
 * Created by Andrew on 1/9/2016.
 */
public class FacebookClient {

    private CloseableHttpClient httpClient;
    private String email;
    private String pass;
    private String startParams;
    private SAXBuilder saxBuilder;
    private Pattern prevUrlPattern = Pattern.compile("(/messages/read/[^\"]+last_message_timestamp[^\"]+)");

    public FacebookClient(String email, String pass, String startParams) {
        this.httpClient = HttpClients.createDefault();
        this.saxBuilder = new SAXBuilder();
        this.email = email;
        this.pass = pass;
        this.startParams = startParams;
    }

    public void startFetch() {
        File dir = new File("messages");
        dir.mkdir();
        login();
        fetch(startParams, 0);
    }
    private void login() {
        List<NameValuePair> postParameters = new ArrayList<NameValuePair>();
        postParameters.add(new BasicNameValuePair("email", email));
        postParameters.add(new BasicNameValuePair("pass", pass));
        String facebookLogin = "https://m.facebook.com/login.php";
        HttpPost httpPost = new HttpPost(facebookLogin);
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
            CloseableHttpResponse response = httpClient.execute(httpPost);
            response.close();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetch(String queryParams, int count) {
        count = count+1;
        String facebookMessages = "https://m.facebook.com/messages/read/";
        HttpGet httpGet = new HttpGet(facebookMessages +"?"+queryParams);
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String responseData = EntityUtils.toString(response.getEntity());

            File output = new File("messages/messages"+count+".html");
            output.createNewFile();
            FileWriter fw = new FileWriter(output.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(responseData);
            bw.flush();
            bw.close();

            Matcher m = prevUrlPattern.matcher(responseData);
            if(m.find()) {
                fetch(m.group(0).split("\\?")[1].replace("&amp;", "&"),count);
            }
            //Document doc = saxBuilder.build(response.getEntity().getContent());
            //doc.getRootElement();
        } catch (IOException e) {
            e.printStackTrace();
//        } catch (JDOMException e) {
//            e.printStackTrace();
        }
    }

    public void finalize() throws Throwable {
        httpClient.close();
        super.finalize();
    }
}
