package marshall.backup.messenger.webclient;

import com.google.gson.Gson;
import marshall.backup.messenger.Utils;
import marshall.backup.messenger.model.DataStore;
import marshall.backup.messenger.model.FileData;
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
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for fetching messages from FB
 * Created by Andrew on 1/9/2016.
 */
public class FacebookClient {

    private CloseableHttpClient httpClient;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36";
    private String email;
    private String pass;
    private String startParams;
    private String dirName;

    private Pattern prevUrlPattern = Pattern.compile("(/messages/read/[^\"]+last_message_timestamp[^\"]+)");
    private Pattern imageUrlPattern = Pattern.compile("(/messages/attachment_preview[^\"]+)");
    private Pattern gifUrlPattern = Pattern.compile("([^\"]+gif[^\"]+)");
    private Pattern imageDownloadPattern = Pattern.compile("(href=\"https://scontent[^\"]+)");

    private File dir;
    private SAXReader reader;
    private Gson gson;
    private NavigableMap<Integer,TreeMap<Integer,TreeMap<Date, FileData>>> messages;
    private Map<String, String> imageDirMapping;

    public FacebookClient(String email, String pass, String startParams, String dirName) {
        this.httpClient = HttpClients.createDefault();
        this.email = email;
        this.pass = pass;
        this.startParams = startParams;
        this.dirName = dirName;
        reader = new SAXReader();
        gson = new Gson();
        messages = new TreeMap<Integer,TreeMap<Integer,TreeMap<Date, FileData>>>();
        imageDirMapping = new HashMap<String, String>();
    }

    public void start() {
        dir = new File("messages/"+dirName);
        dir.mkdir();
        login();
        fetch(startParams, 0);
        write();
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
            log("Starting Login");
            httpPost.setHeader("User-Agent", userAgent);
            httpPost.setEntity(new UrlEncodedFormEntity(postParameters));
            CloseableHttpResponse response = httpClient.execute(httpPost);
            response.close();
            log("Login Successful");
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
        httpGet.setHeader("User-Agent", userAgent);
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String responseData = EntityUtils.toString(response.getEntity());
            responseData = responseData.replaceAll("<!DOCTYPE[^>]+>","");
            responseData = responseData.replaceAll("<head.+</head>", "");
            responseData = responseData.replaceAll("<script[^<]+</script>", "");
            responseData = responseData.replaceAll("&nbsp;","&#160;");
            responseData = responseData.replaceAll("&shy;","&#173;");

            log("Starting processing, iteration " + count);
            processMessages(responseData);
            log("Completed " + count + " processing. Currently at "
                    + messages.firstEntry().getValue().firstEntry().getValue().firstEntry().getKey());
            saveString(dir.getAbsolutePath()+"/messages"+count+".html", responseData);
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

    private void processMessages(String responseData) {
        try {
            Document document = reader.read(new StringReader(responseData));
            List<Node> messageNodes = document.selectNodes("//div[@id='root']//div[@data-sigil='message-xhp marea']");
            for(Node n : messageNodes) {
                Element e = (Element)n;
                DataStore xhpData = gson.fromJson(e.attributeValue("data-store"), DataStore.class);
                boolean hasAttachments = xhpData.hasAttachment();
                FileData fd = new FileData();
                fd.setSender(xhpData.getName());
                Element messageText = (Element)e.selectSingleNode("descendant::div[@data-sigil='message-text']");
                DataStore messageData = gson.fromJson(messageText.attributeValue("data-store"), DataStore.class);
                fd.setSendDate(messageData.getTimestamp());
                fd.setMessage(messageText.getStringValue());
                if(hasAttachments) {
                    List<Node> images = messageText.selectNodes("descendant::img");
                    for(Node image : images) {
                        Element eImage = (Element)image;
                        String[] imagePaths = eImage.attributeValue("src").split("\\?")[0].split("/");
                        fd.addAttachedImage(imagePaths[imagePaths.length - 1]);
                    }
                }
                Calendar cal = Calendar.getInstance();
                cal.setTime(fd.getSendDate());
                int year = cal.get(Calendar.YEAR);
                int month = cal.get(Calendar.MONTH)+1;
                if(messages.get(year) == null) {
                    messages.put(year, new TreeMap<Integer, TreeMap<Date, FileData>>());
                }
                if(messages.get(year).get(month) == null) {
                    messages.get(year).put(month, new TreeMap<Date, FileData>());
                }
                messages.get(year).get(month).put(fd.getSendDate(),fd);
                for(String imagePath : fd.getAttachedImages()) {
                    imageDirMapping.put(imagePath,Integer.toString(year));
                }
            }
        } catch (DocumentException e) {
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
                fetchImage(gif.group(0));
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
            saveImage(dir.getAbsolutePath()+"/"+imageDirMapping.get(fileName)+"/"+fileName, imgBytes);
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
        output.getParentFile().mkdir();
        output.createNewFile();
        FileOutputStream fos = new FileOutputStream(output.getAbsoluteFile());
        fos.write(data);
        fos.flush();
        fos.close();
    }

    private void write() {
        log("Starting Data Write");
        while(messages.size() > 0) {
            Map.Entry<Integer,TreeMap<Integer,TreeMap<Date,FileData>>> e = messages.pollFirstEntry();
            String yearPath = dir.getAbsolutePath() + "/" + e.getKey();
            File year = new File(yearPath);
            year.mkdir();
            log("Starting year " + e.getKey());
            TreeMap<Integer,TreeMap<Date, FileData>> monthMap = e.getValue();
            while(monthMap.size() > 0) {
                Map.Entry<Integer,TreeMap<Date,FileData>> me = monthMap.pollFirstEntry();
                String monthPath = year.getAbsolutePath() + "/" + me.getKey()+".html";
                TreeMap<Date, FileData> messageMap = me.getValue();
                StringBuilder fileContents = new StringBuilder();
                log("Starting month " + me.getKey());
                while (messageMap.size() > 0) {
                    FileData message = messageMap.pollFirstEntry().getValue();
                    fileContents.append("<h1>"+message.getSender()+"</h1>");
                    fileContents.append("<h2>"+message.getSendDate()+"</h2>");
                    fileContents.append("<div>"+message.getMessage()+"</div>");
                    if(message.hasAttachedImage()) {
                        fileContents.append("<div>Images<ul>");
                        for (String img : message.getAttachedImages()) {
                            fileContents.append("<li><img src=\""+img+"\"/></li>");
                        }
                        fileContents.append("</ul><div>");
                    }
                }
                log("Finished month " + me.getKey());
                try {
                    saveString(monthPath, fileContents.toString());
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

            }
            log("Finished year " + e.getKey());
        }
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

    private void log(String message) {
        System.out.println(message);
    }
}
