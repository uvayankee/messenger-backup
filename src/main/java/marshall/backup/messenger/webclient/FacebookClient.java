package marshall.backup.messenger.webclient;

import marshall.backup.messenger.Utils;
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
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.jaxen.FunctionContext;
import org.jaxen.NamespaceContext;
import org.jaxen.VariableContext;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private Pattern prevUrlPattern = Pattern.compile("(/messages/read/[^\"]+last_message_timestamp[^\"]+)");
    private Pattern imageUrlPattern = Pattern.compile("(/messages/attachment_preview[^\"]+)");
    private Pattern gifUrlPattern = Pattern.compile("([^\"]+gif[^\"]+)");
    private Pattern imageDownloadPattern = Pattern.compile("(href=\"https://scontent[^\"]+)");

    public FacebookClient(String email, String pass, String startParams) {
        this.httpClient = HttpClients.createDefault();
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
    public void finalize() throws Throwable {
        httpClient.close();
        super.finalize();
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
        HttpGet httpGet = new HttpGet(Utils.fixAmpersands(facebookMessages +"?"+queryParams));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String responseData = EntityUtils.toString(response.getEntity());
            responseData = responseData.replace("<!DOCTYPE html>","<?xml version=\"1.0\"?>");
            responseData = responseData.replaceAll("<head[^<]+</head>", "");
            responseData = responseData.replaceAll("<script[^<]+</script>", "");
            responseData = responseData.replaceAll("&nbsp;","&#160;");

            saveString("messages/messages"+count+".html", responseData);
            Matcher img = imageUrlPattern.matcher(responseData);
            while(img.find()) {
                fetchImagePreview(img.group(0).split("\\?")[1]);
            }

            Matcher prev = prevUrlPattern.matcher(responseData);
            if(prev.find()) {
                fetch(prev.group(0).split("\\?")[1],count);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fetchImagePreview(String queryParams) {
        String facebookImagePreview = "https://m.facebook.com/messages/attachment_preview/";
        HttpGet httpGet = new HttpGet(Utils.fixAmpersands(facebookImagePreview +"?"+queryParams));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String responseData = EntityUtils.toString(response.getEntity());
            Matcher m = imageDownloadPattern.matcher(responseData);
            while(m.find()) {
                String imageUrl = m.group(0).replace("href=\"","");
                fetchImage(imageUrl);
            }
            Matcher gif = gifUrlPattern.matcher(responseData);
            while (gif.find()) {
                fetchImage(m.group(0));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void fetchImage(String imageUrl) {
        String[] paths = imageUrl.split("\\?")[0].split("/");
        String fileName = paths[paths.length-1];
        HttpGet httpGet = new HttpGet(Utils.fixAmpersands(imageUrl));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            byte[] imgBytes = EntityUtils.toByteArray(response.getEntity());
            saveImage("messages/"+fileName, imgBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void saveString(String fileName, String data) throws IOException {
        File output = new File(fileName);
        output.createNewFile();
        FileWriter fw = new FileWriter(output.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(data);
        bw.flush();
        bw.close();
    }

    private void saveImage(String fileName, byte[] data) throws IOException {
        File output = new File(fileName);
        FileOutputStream fos = new FileOutputStream(output.getAbsoluteFile());
        fos.write(data);
        fos.flush();
        fos.close();
    }

    public void testFile() {
        try {
            long startDt = System.currentTimeMillis();
            File message_test = new File("message-test.html");
            SAXReader reader = new SAXReader();
            Document document = reader.read(message_test);
            long parseDt = System.currentTimeMillis();
            System.out.println(parseDt-startDt);
            List<Node> messages = document.selectNodes("//div[@id='root']//div[@class='voice acw abt']");
            System.out.println(messages.size());
            for(Node message : messages) {
                System.out.println(message.getStringValue());
            }
            long endDt = System.currentTimeMillis();
            System.out.println(endDt - startDt);
            System.out.println(document.getXMLEncoding());
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        //Deleted head
        //Deleted <script[^<]+</script>
        //Replaced &nbsp; with &#160;
    }
}
