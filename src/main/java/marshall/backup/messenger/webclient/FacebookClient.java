package marshall.backup.messenger.webclient;

import com.google.gson.Gson;
import marshall.backup.messenger.Utils;
import marshall.backup.messenger.model.DataStore;
import marshall.backup.messenger.model.FileData;
import marshall.backup.messenger.model.ImageData;
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
    private Pattern pngUrlPattern = Pattern.compile("([^\"]+png[^\"]+)");
    private Pattern imageDownloadPattern = Pattern.compile("(href=\"https://scontent[^\"]+)");

    private File dir;
    private SAXReader reader;
    private Gson gson;
    private NavigableMap<Integer,TreeMap<Integer,TreeMap<Date, FileData>>> messages;

    public FacebookClient(String email, String pass, String startParams, String dirName) {
        this.httpClient = HttpClients.createDefault();
        this.email = email;
        this.pass = pass;
        this.startParams = startParams;
        this.dirName = dirName;
        reader = new SAXReader();
        gson = new Gson();
        messages = new TreeMap<Integer,TreeMap<Integer,TreeMap<Date, FileData>>>();
    }

    public void start() {
        dir = new File("messages/"+dirName);
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
            //saveString(dir.getAbsolutePath()+"/messages"+count+".html", responseData);
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
                if(hasAttachments) {
                    List<Node> imgLinks = messageText.selectNodes("descendant::a[contains(@href,'attachment_preview')]");
                    for(Node link : imgLinks) {
                        Element eLink = (Element)link;
                        String href = eLink.attributeValue("href");
                        if(href != null &&href.contains("attachment_preview")) {
                            ImageData img = fetchImagePreview(href.split("\\?")[1]);
                            fd.addAttachedImage(img);
                        }
                    }
                    List<Node> images = messageText.selectNodes("descendant::img[not(ancestor::a)]");
                    for(Node image : images) {
                        Element eImage = (Element)image;
                        ImageData img = fetchImage(eImage.attributeValue("src"));
                        fd.addAttachedImage(img);
                    }

                }
                List<Node> attachments = messageText.selectNodes("descendant::div[@class='messageAttachments']");
                for (Node attachment : attachments) {
                    attachment.detach();
                }
                List<Node> links = messageText.selectNodes("descendant::a");
                for (Node link : links) {
                    Element eLink = (Element)link;
                    String linkText = eLink.getStringValue();

                }
                StringWriter sw = new StringWriter();
                messageText.write(sw);
                fd.setMessage(sw.toString());

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
            }
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ImageData fetchImagePreview(String queryParams) {
        String facebookImagePreview = "https://m.facebook.com/messages/attachment_preview/";
        HttpGet httpGet = new HttpGet(Utils.fixAmpersands(facebookImagePreview +"?"+queryParams));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            String responseData = EntityUtils.toString(response.getEntity());
            Matcher m = imageDownloadPattern.matcher(responseData);
            if (m.find()) {
                String imageUrl = m.group(0).replace("href=\"","");
                return fetchImage(imageUrl);
            }
            Matcher gif = gifUrlPattern.matcher(responseData);
            if (gif.find()) {
                return fetchImage(gif.group(0));
            }
            Matcher png = pngUrlPattern.matcher(responseData);
            if (png.find()) {
                return fetchImage(png.group(0));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("Unable to process image found in preview");
        }
        throw new IllegalStateException("No image found in preview");
    }

    private ImageData fetchImage(String imageUrl) {
        ImageData img = new ImageData();
        String[] paths = imageUrl.split("\\?")[0].split("/");
        String fileName = paths[paths.length-1];
        img.setFullFileName(fileName);
        HttpGet httpGet = new HttpGet(Utils.fixAmpersands(imageUrl));
        try {
            CloseableHttpResponse response = httpClient.execute(httpGet);
            byte[] imgBytes = EntityUtils.toByteArray(response.getEntity());
            img.setImage(imgBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return img;
    }


    private void saveString(String fileName, String data) throws IOException {
        File output = new File(fileName);
        touch(output);
        FileWriter fw = new FileWriter(output.getAbsoluteFile());
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(data);
        bw.flush();
        bw.close();
    }

    private void saveImage(String fileName, byte[] data) throws IOException {
        File output = new File(fileName);
        makeDir(output.getParentFile());
        touch(output);
        FileOutputStream fos = new FileOutputStream(output.getAbsoluteFile());
        fos.write(data);
        fos.flush();
        fos.close();
    }

    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    private void write() {
        log("Starting Data Write");
        makeDir(dir);
        while(messages.size() > 0) {
            Map.Entry<Integer,TreeMap<Integer,TreeMap<Date,FileData>>> e = messages.pollFirstEntry();
            String yearPath = dir.getAbsolutePath() + "/" + e.getKey();
            File year = new File(yearPath);
            makeDir(year);
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
                    fileContents.append("<h1>"+message.getSender()+"</h1>"+
                            "<h2>"+message.getSendDate()+"</h2>"+
                            "<div>"+message.getMessage()+"</div>");
                    if(message.hasAttachedImage()) {
                        fileContents.append("<table><caption>Images</caption><tr>");
                        int imgCount = 0;
                        for (ImageData img : message.getAttachedImages()) {
                            imgCount++;
                            if(imgCount > 1 && imgCount % 3 == 1) {
                                fileContents.append("</tr><tr>");
                            }
                            fileContents.append("<td><a href=\""+img.getFullFileName()+
                                    "\"><img width=\"300\" src=\""+img.getFullFileName()+"\"/></a></td>");
                            try {
                                saveImage(year.getAbsolutePath()+"/"+img.getFullFileName(), img.getImage());
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                        fileContents.append("</tr></table>");
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

    private void log(String message) {
        System.out.println(message);
    }

    private void makeDir(File directory) {
        if(!directory.mkdir()) {
            if(!directory.exists()) {
                System.err.println("Unable to make directory: " + directory.getAbsolutePath());
                throw new IllegalStateException("Unable to make directory: " + directory.getAbsolutePath());
            }
        }
    }

    private void touch(File file) throws IOException {
        if(!file.createNewFile()) {
            if (!file.exists()) {
                System.err.println("Unable to make file: " + file.getAbsolutePath());
                throw new IllegalStateException("Unable to make file: " + file.getAbsolutePath());
            }
        }
    }
}
