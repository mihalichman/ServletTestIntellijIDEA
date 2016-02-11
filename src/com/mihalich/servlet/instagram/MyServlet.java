package com.mihalich.servlet.instagram;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.util.*;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
/**
 * Created by mihalich on 09.02.2016.
 */
@WebServlet("/MyServlet")
public class MyServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final String USER_AGENT = "Mozilla/5.0";

    //Данные для подключения к Instagram API
    private final String CLIENT_ID = "54c66884e1154304a0a7f82ec312c07c";
    private final String CLIENT_SECRET = "91fc22a538e84e2f91ec53fdcdc0324e";
    private final String REDIRECT_URI = "http://localhost";
    private final String ACCESS_TOKEN = "2898235952.5b9e1e6.0ae80b70367c43248d9af8571dccb678";
    /**
     * Default constructor.
     */
    public MyServlet() {
        // TODO Auto-generated constructor stub
    }
    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!request.getParameter("username").isEmpty())//проверка на введенное имя
        {
            String username = (String) request.getParameter("username");

            String userID = "https://api.instagram.com/v1/users/search?q="+username+"&access_token="+ACCESS_TOKEN;
            String userid = getID(userID, username);
            System.out.println("userID === " + userid);
            if (!(userid==null)) //проверка на получение ID пользователя, если значение пустое то либо нет такого пользователя либо не публичен
            {
                //получение токена для доступа
                //String token = (String) request.getParameter("token");
                //String userid = (String) request.getParameter("user-id");
                //String code = "https://api.instagram.com/oauth/authorize/?client_id="+CLIENT_ID+"&redirect_uri="+REDIRECT_URI+"&response_type=code&scope=follower_list+comments+likes";

                // Строка запроса для получения основных данных пользователя
                String code = "https://api.instagram.com/v1/users/"+userid+"/?access_token="+ACCESS_TOKEN;
                Date start = new Date();//время начала запросов
                //Получение основных данных по пользователю
                String[] countData = getCounts(code);
                int followed = Integer.parseInt(countData[0]);
                int follows = Integer.parseInt(countData[1]);
                String name = countData[2];

                //Строка запроса для получения данных по последним 30 постам
                String reqMedia = "https://api.instagram.com/v1/users/"+userid+"/media/recent/?access_token="+ACCESS_TOKEN+"&count=30";

                //Получение медиа данных по пользователю (комментарии, лайки, тексты комментариев) за последние 30 постов
                ArrayList<String> mediaData = getMedia(reqMedia);
                int comments30 = Integer.parseInt(mediaData.get(0));
                int likes30 = Integer.parseInt(mediaData.get(1));
                String split = mediaData.get(2);
                Date end = new Date();//время окончания запросов

                //Скоринг
                double score = getScore (followed, follows, likes30);
                ArrayList<String> commList = new ArrayList<>(Arrays.asList(split));
                Map<String,Integer> wordRepeate2 = getWordRepeate(commList);

                //затраченное время на запросы
                long ftime = start.getTime()-end.getTime();
                System.out.println("Время затраченное на запросы"+ftime+"ms");

                //Запись в базу данных
                writeToDB( name, followed, follows, comments30, likes30,commList);

                //Блок проверки
                System.out.println("token" + ACCESS_TOKEN +"user-id" + userid);
                System.out.println("Name :---> " +name);
                System.out.println("followed_by :---> " +followed);
                System.out.println("follows :---> " +follows);
                System.out.println("Comment over 30 posts :---> " +comments30);
                System.out.println("Likes over 30 posts :---> " +likes30);
                System.out.println("comments over 30 posts :---> " +commList);
                System.out.println("Score :---> " +score);

                System.out.println("REPEATE2");
                for(Map.Entry<String, Integer> entry : wordRepeate2.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();
                    System.out.println("Word " +key+" Повторяется " + value+" раз");
                }
                //Конец блока проверки

                //Возврат информации на запрос из основной страницы
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().println("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><p>" + username);
                response.getWriter().println("<p> Имя: " + name + " ");
                response.getWriter().println("Фоловеров: " + followed + " ");
                response.getWriter().println("Фоловьемых: " + follows + " ");
                response.getWriter().println("Лайков: " + likes30 + "<br>");
                response.getWriter().println("Баллов: " + score + "<br>");
                response.getWriter().println("Повторяющиеся слова: ");
                for(Map.Entry<String, Integer> entry : wordRepeate2.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();
                    response.getWriter().println(key+" -- "+value+" раза, ");
                }
                response.getWriter().println("</p></body></html>");
            }
            else
            {
                //Возврат в случае отсутсвия пользователя
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().println("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><p>");
                response.getWriter().println("<p>Имя не найдено или доступ к нему закрыт</p>");
                response.getWriter().println("</p></body></html>");
            }
        }
        else
        {
            //Возврат в случае пустой строки
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().println("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body><p>");
            response.getWriter().println("<p>Вы не ввели имя</p>");
            response.getWriter().println("</p></body></html>");
        }
    }
    /**
     *
     * @param searchnameURL: Строка запроса для получния ID введенного пользователя
     * @param searchUser: Введенный пользователь
     * @param param: Параметр, что плоучить на выходе (0-базовая инфа, 1 фолловеры)
     * @return : String значение ID
     * @throws Exception, JSONExceptio
     */

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // TODO Auto-generated method stub
        doGet(request, response);
    }
    /**
     * Запись параметров в базу данных
     * @param searchnameURL: Строка запроса для получния ID введенного пользователя
     * @param searchUser: Введенный пользователь
     * @return Айди пользователя
     */
    private String getID(String searchnameURL, String searchUser) throws JSONException, IOException {
        String resp = null;
        JSONObject jsonComm = new JSONObject(getjson(searchnameURL).toString());
        JSONArray dataComm = jsonComm.getJSONArray("data");
        for (int i=0; i< dataComm.length();i++)
        {
            String username = dataComm.getJSONObject(i).getString("username");
            if (username.equals(searchUser))
            {
                resp = dataComm.getJSONObject(i).getString("id");
                break;
            }
        }
        return resp;
    }
    /**
     * Запись параметров в базу данных
     * @param name: Строка запроса для получния ID введенного пользователя
     * @param followed: Введенный пользователь
     * @param follows: Параметр, что плоучить на выходе (0-базовая инфа, 1 фолловеры)
     * @param comments30: Параметр, что плоучить на выходе (0-базовая инфа, 1 фолловеры)
     * @param likes30: Параметр, что плоучить на выходе (0-базовая инфа, 1 фолловеры)
     */
    private void writeToDB(String name, int followed, int follows, int comments30, int likes30,
                           ArrayList<String> commentsTexts30) {

        try {
            DBConnection con = new DBConnection();
            Connection connection = con.getConn();
            con.insertRow(connection, name , followed, follows, comments30, likes30, commentsTexts30);
        }
        catch (Exception e) {
            //e.printStackTrace();
            //out.print(e.toString());
            System.out.println(e.getMessage());
        }
    }
    /**
     * Получение коллекции
     * @param commentsTexts30: Массив коментарие за на последние 30 постов
     * @return Коллекция с ключами-слово и значениями-количество повторений
     */
    private Map<String, Integer> getWordRepeate(ArrayList<String> commentsTexts30) {

        Map<String,Integer> mapWords = new HashMap<String, Integer>();
        ArrayList<String> words = new ArrayList<>();
        for (int i=0; i<commentsTexts30.size();i++)
        {
            String strtrim = commentsTexts30.get(i).trim();
            String[] str = strtrim.split(" ");
            for (int j=0; j<str.length;j++){
                if (!str[j].startsWith("@"))
                {
                    String result = str[j].replaceAll("\\!|\\.|\\,|\\?", "").toLowerCase().trim();
                    if (!result.equals("?")||!result.equals(""))
                    {
                        words.add(result);
                    }
                }
            }
        }
        for (int i=0; i<words.size();i++)
        {
            int wCount = 0;
            for (int j=0; j<words.size();j++)
            {
                if (words.get(i).equals(words.get(j)))
                {
                    wCount ++;
                }
            }
            if (wCount>1){
                mapWords.put(words.get(i), wCount);
            }

        }
        return mapWords;
    }
    /**
     *
     * Скоринг
     * @param followed: количество фоловеров
     * @param follows:количество фоловьемых
     * @param likes30:количество лайков на последние 30 постов
     * @return баллы на основе данных
     *
     */
    private double getScore(int followed, int follows, int likes30) {
        double score = 1;
        if (followed >500)
        {
            int tempfd = (followed/500);
            if(tempfd >= 5)
            {
                score += 0.5;
            }
            else
            {
                score += (tempfd/10);
            }
        }
        else
        {
            if (followed<50)
            {
                score += -0.2;
            }
        }
        System.out.println("Score after fd:---> " +score);
        if (follows>200)
        {
            int fs = (follows/200);
            if (fs>3)
            {
                score -= 0.3;
            }
            else
            {
                score -= (fs/10);
            }
        }
        System.out.println("Score after fs:---> " +score);
        if (likes30>30)
        {
            int lk = ((likes30/30-30)/10);
            System.out.println("Score after likes:---> " +score + lk);
            score += (double) lk/10;
            System.out.println("Score after lk/10:---> " +score);
        }
        else
        {
            if (likes30<10)
            {
                score -= 0.3;
            }
            else if (likes30<20)
            {
                score -= 0.2;
            }
            else if (likes30<30)
            {
                score -= 0.1;
            }
        }
        return score;
    }
    /**
     *
     * Получения количества комментариев, количества лайков и текстов последних 30ти комментариев
     * @param reqMedia: строка запроса для получения данных
     * @return Массив с данными - количество комментариев, колличество лайков, тексты комментариев,
     * ArrayList<i=0{количество комментариев}>
     * ArrayList<i=1{количество лайков}>
     * ArrayList<i=0{количество тексты комментариев}>
     */
    private ArrayList<String> getMedia(String reqMedia) throws JSONException, IOException {
        ArrayList<String> resp = new ArrayList<>();
        JSONObject jsonComm = new JSONObject(getjson(reqMedia).toString());
        JSONArray dataComm = jsonComm.getJSONArray("data");
        int commcount = 0;
        int likecount = 0;
        String commtext = null;
        for (int i=0; i< dataComm.length();i++)
        {
            JSONObject countsComm = dataComm.getJSONObject(i).getJSONObject("comments");
            commcount += countsComm.getInt("count");
            JSONArray texts = countsComm.getJSONArray("data");
            for (int j=0; j<texts.length();j++)
            {
                String commTexts = texts.getJSONObject(j).getString("text");
                commtext += " "+commTexts;
            }
            JSONObject countsLikes = dataComm.getJSONObject(i).getJSONObject("likes");
            likecount +=countsLikes.getInt("count");
        }
        resp.add(String.valueOf(commcount));
        resp.add(String.valueOf(likecount));
        resp.add(commtext);

    return resp;
    }
    /**
     *
     * Получения основных данных по пользователю
     * @param request: строка запроса для получения данных
     * @return String [] - массив данных ({фоловеры},{фоловьемые},{полное имя})
     *
     */
    public String[] getCounts(String request) throws JSONException, IOException
    {
        String[] res = new String[3];
        JSONObject json = new JSONObject(getjson(request).toString());
        JSONObject data = json.getJSONObject("data");
        JSONObject counts = data.getJSONObject("counts");
        res[0] = Integer.toString(counts.getInt("followed_by"));
        res[1] = Integer.toString(counts.getInt("follows"));
        res[2] = data.getString("full_name");
        return res;
    }
    /**
     *
     * Получения массива данных на основе запроса
     * @param urlj: строка запроса
     * @return JSON массив
     *
     */
    public StringBuffer getjson (String urlj) throws IOException
    {
        URL url = new URL(urlj);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer res = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            res.append(inputLine);
        }
        in.close();
        return res;
    }
}
