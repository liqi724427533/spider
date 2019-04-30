package com.lifesea.web.information.spider.yidianzixun;  
  
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigProperty;
import org.springframework.context.SpringContextHolder;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lifesea.api.information.utils.cosine.CosineSimilarAlgorithmUills;
import com.lifesea.api.information.utils.es.ElasticsearchUtils;
import com.lifesea.api.information.utils.spider.FileUtil;
import com.lifesea.api.information.utils.spider.UrlUtils;
import com.lifesea.api.information.view.hinfo.bean.DHinfoBase;
import com.lifesea.api.information.view.hinfo.bean.DHinfoCon;
import com.lifesea.api.information.view.hinfo.service.DHinfoService;
import com.lifesea.common.util.StringUtil;
import com.lifesea.common.utils.CommonUtil;
import com.lifesea.common.utils.DateUtil;
import com.lifesea.web.information.spider.pipeline.spiderHinfoPipeline;

import us.codecraft.webmagic.Page;  
import us.codecraft.webmagic.Site;  
import us.codecraft.webmagic.Spider;  
import us.codecraft.webmagic.processor.PageProcessor;  
@Component("yidianzixun")
public class yidianzixun implements PageProcessor {  
	
	private final Logger logger = LoggerFactory.getLogger(yidianzixun.class);
	
	String[] classarray = { "热门","商业职场","休闲生活","时尚","美食", "育儿", "两性", "情感","健康"};
	String  cdHinfoTypeCds = "{\"雾霾\":\"02\","
			+ "\"医药行业\":\"02\",\"瑜伽\":\"10\",\"生活\":\"02\",\"手工\":\"02\",\"骑行\":\"03\",\"太极拳\":\"03\",\"潜水\":\"03\",\"型男\":\"08\",\"饮茶\":\"04\","
			+ "\"美容\":\"11\",\"化妆\":\"11\",\"丰胸\":\"11\",\"美体塑形\":\"10\","
			+ "\"养生食谱\":\"04\",\"减肥食谱\":\"10\",\"美食\":\"06\",\"家常菜\":\"06\",\"饮食禁忌\":\"04\",\"煲汤\":\"06\",\"火锅\":\"06\",\"甜点\":\"06\",\"西餐\":\"06\",\"烧烤\":\"06\",\"川菜\":\"06\",\"韩国料理\":\"06\",\"湘菜\":\"06\",\"粤菜\":\"06\",\"鲁菜\":\"06\",\"老人饮食\":\"04\","
			+ "\"育儿\":\"13\","
			+ "\"男性健康\":\"08\",\"女性健康\":\"07\",\"妇科\":\"07\",\"癌症\":\"14\",\"糖尿病\":\"14\",\"健身\":\"03\",\"养生\":\"04\",\"疾病\":\"14\","
			+ "\"心理健康\":\"14\",\"失眠\":\"14\",\"颈椎\":\"14\",\"中医\":\"14\",\"针灸\":\"02\",\"口腔\":\"14\",\"感冒\":\"14\","
			+ "\"恋爱\":\"12\",\"婚姻\":\"12\",\"情感心理\":\"12\",\"暗恋\":\"12\",\"失恋\":\"12\",\"求婚\":\"12\",\"出轨\":\"12\",\"剩男剩女\":\"09\",\"单身\":\"09\",\"婆媳\":\"12\"}";

	private Site site = Site.me().setRetryTimes(10).setCycleRetryTimes(3).setSleepTime(1000).setCharset("utf-8");
	
	private DHinfoService dHinfoService = SpringContextHolder.getBean("dHinfoService");

	public Site getSite() {
		return site;
	}
	
	private static Spider spider = null;
	
	private static int repetitions= 0; 
	
	private static int maxRepetitions= 0;
	
	private static String filepath = null;
	
	private static String esindex = null;
 
    public void process(Page page) {
    	
    	String url = page.getUrl().toString();
    	logger.debug("解析地址:"+url);
    	if("http://www.yidianzixun.com/channellist".equals(url)){
			Document doc = Jsoup.parse(page.getHtml().toString());
			Elements jselements = doc.select("script");
			for (Element script : jselements) {
				if (script.html().contains("window.yidian.docinfo =")) 
				{
					String str = script.html().replace("\n", ""); 
					String pattern = "window.yidian.docinfo = \\{.*?\\}]}"; 
					Pattern r = Pattern.compile(pattern, Pattern.MULTILINE);
					Matcher m = r.matcher(str);
					if (m.find()) {
						String option_1 = m.group();
						JSONObject jsonobject = JSON.parseObject(option_1.split("=")[1]);
						Map<String, Object> cdHinfoTypeCdobj = JSON.parseObject(cdHinfoTypeCds);
						String[] cclassarray = {"雾霾",
								"商业职场",
								"瑜伽","生活","手工","骑行","太极拳","潜水","型男","饮茶",
								"美容","化妆","丰胸","美体塑形",
								"养生食谱","美食","家常菜","饮食禁忌","煲汤","火锅","甜点","西餐","烧烤","川菜","韩国料理","湘菜","粤菜","鲁菜","老人饮食",
								"恋爱","婚姻","情感心理","暗恋","失恋","求婚","婆媳","剩男剩女","同性婚姻","出轨","单身",
								"中医","疾病","养生","健身","男性健康","女性健康","心理健康","癌症","糖尿病","妇科","感冒","失眠","口腔","颈椎"};
						JSONArray jsonArray = jsonobject.getJSONArray("categories");
						String cdHinfoType = null;
						for (int i = 0; i < jsonArray.size(); i++) {
							JSONObject job = jsonArray.getJSONObject(i);
							if (Arrays.asList(classarray).contains(job.getString("category"))) {
								String urltmp = "http://www.yidianzixun.com/home/q/news_list_for_channel?channel_id=u675&cstart=0&cend=10&infinite=true&refresh=1&__from__=pc&multi=5&appid=web_yidian&cdHinfoType=01&_=1501485184129";
								
								JSONArray channels = job.getJSONArray("channels");
								if("育儿".equals(job.getString("category"))){
									cdHinfoType =(String) cdHinfoTypeCdobj.getOrDefault(job.getString("category"), "16");
									for (int j = 0; j < channels.size(); j++) {
										JSONObject channel = channels.getJSONObject(j);
										String tagrqurl =UrlUtils.replaceurlparam(urltmp, "channel_id",
												channel.getString("id"));
										tagrqurl = UrlUtils.replaceurlparam(tagrqurl, "cdHinfoType",
												cdHinfoType);
										logger.debug("添加爬取页面，地址："+tagrqurl);
										page.addTargetRequest(tagrqurl);
									}
								}else{
									for (int j = 0; j < channels.size(); j++) {
										JSONObject channel = channels.getJSONObject(j);
										if (Arrays.asList(cclassarray).contains(channel.getString("name"))) {
											cdHinfoType =(String) cdHinfoTypeCdobj.getOrDefault(channel.getString("name"), "16");
											String tagrqurl =UrlUtils.replaceurlparam(urltmp, "channel_id",
													channel.getString("id"));
											tagrqurl = UrlUtils.replaceurlparam(tagrqurl, "cdHinfoType",
													cdHinfoType);
											logger.debug("添加爬取页面，地址："+tagrqurl);
											page.addTargetRequest(tagrqurl);
										}
									}
								}
								
							}

						}
					}
				}
			}
    	}else if(page.getUrl().regex("channel_id=[A-Za-z0-9]{2,18}").match()){
    		String htmlbody = page.getHtml().getDocument().text();
    		JSONObject jsonobject = JSON.parseObject(htmlbody); 
    		if("success".equals(jsonobject.get("status"))){
    			JSONArray jsonArray = jsonobject.getJSONArray("result");
    			String ctype = null;
    			String like = null;
    			String up = null;
    			String commentcount = null;
    			for(int i=1;i<jsonArray.size();i++){
    				JSONObject job = jsonArray.getJSONObject(i); 
    				ctype = job.getString("ctype");
    				like = job.getString("like");
    				if("null".equals(like)||StringUtil.isNullOrEmpty(like)){
    					like=""+0;
    				}
    				up = job.getString("up");
    				if("null".equals(up)||StringUtil.isNullOrEmpty(up)){
    					up=""+0;
    				}
    				commentcount = job.getString("comment_count");
    				if("null".equals(commentcount)||StringUtil.isNullOrEmpty(commentcount)){
    					commentcount=""+0;
    				}
    				if(job.containsKey("url")&&!job.containsKey("wm_copyright")){
    					String tagrqurl = job.getString("url");
        				if(tagrqurl.startsWith("http://www.yidianzixun.com")){
        					tagrqurl+="&ctype="+ctype+"&cdHinfoType="+UrlUtils.getQueryString(url,"cdHinfoType");
        					tagrqurl+="&like="+like;
        					tagrqurl+="&up="+up;
        					tagrqurl+="&commentcount="+commentcount;
        					logger.debug("添加爬取页面，地址："+tagrqurl);
        					page.addTargetRequest(tagrqurl); 
        				}else if(tagrqurl.endsWith(".html")){
        					tagrqurl+="?ctype="+ctype+"&cdHinfoType="+UrlUtils.getQueryString(url,"cdHinfoType");
        					tagrqurl+="&like="+like;
        					tagrqurl+="&up="+up;
        					tagrqurl+="&commentcount="+commentcount;
        					logger.debug("添加爬取页面，地址："+tagrqurl);
        					page.addTargetRequest(tagrqurl); 
        				}
        				
    				}
    				
    			}
    			Map<String, String> urlparmes = UrlUtils.URLRequest(url);
    			url  = UrlUtils.UrlPage(url);
    			url = url+"?";
    			for(String strRequestKey: urlparmes.keySet()) {
    				String strRequestValue=urlparmes.get(strRequestKey);
    				if(strRequestKey.equals("cstart")){
    					int cstart = Integer.parseInt(strRequestValue)+10;
    					strRequestValue = Integer.toString(cstart);
    				}
    				if(strRequestKey.equals("cend")){
    					int cend = Integer.parseInt(strRequestValue)+10;
    					strRequestValue = Integer.toString(cend);
    				}
    				url+=strRequestKey+"="+strRequestValue+"&";           
    			}
    			
    			url = url.substring(0, url.length()-1);
    			logger.debug("添加爬取页面，地址："+url);
    			page.addTargetRequest(url);
    		}
    	}else{
        	Document doc = Jsoup.parse(page.getHtml().toString());
        	page.putField("isOwn", false);
        	if(url.startsWith("http://www.yidianzixun.com")){
        		
        		Element element = doc.select("div.left-wrapper").first();
        		String title = element.select("h2").first().text();
        		if(title.contains("直播预告")){
        			return;
        		}
        		page.putField("hinfoTitle", element.select("h2").first().text());
        		page.putField("hinfoUrl", url);
        		
        		Element metae = doc.select("div.meta").first();
        		if(metae.select("a.doc-source").isEmpty()){
        			page.putField("source", metae.select("span").first().text());
        			try {
        				page.putField("dtmHinfo", convertStrDateTime(metae.select("span").last().text()));
    				} catch (Exception e) {
    					e.printStackTrace();
    					page.putField("dtmHinfo", new Date());
    				}
        		}else{
        			page.putField("source", metae.select("a.doc-source").first().text());
        			try {
        				page.putField("dtmHinfo", convertStrDateTime(metae.select("span").first().text()));
    				} catch (Exception e) {
    					e.printStackTrace();
    					page.putField("dtmHinfo", new Date());
    				}
        		}
        		
        		
        		if("news".equals(UrlUtils.getQueryString(url,"ctype"))){
        			Element contente = doc.select("div.content-bd").first();
        			Elements contents =  null;
        			if(null!=contente.getElementById("imedia-article")){
        				contents = contente.getElementById("imedia-article").children();
        			}else if(!contente.select("div.imedia-article").isEmpty()){
        				contents = contente.select("div.imedia-article").first().children();
        			}else{
        				contents = contente.children();
        			}
        			
        			JSONArray contentarry = new JSONArray();
        			JSONArray imgarry = new JSONArray();
        			StringBuffer contentstrbf = new StringBuffer();
        			int index = 0;
        			for(Element el : contents){
        				if("script".equals(el.tagName())){
        					continue;
        				}
        				if(null!=el.getElementsByTag("video").first()){
        					page.putField("isExists", true);
        					return;
        				}
        				if(null!=el.getElementsByTag("yd-tag-component").first()){
        					page.putField("isExists", true);
        					return;
        				}
        				JSONObject arryobj = new JSONObject();
        				Element img = el.getElementsByTag("img").first();
        				if(img!=null){
        					if(index==0){
        						el.getElementsByTag("img").remove();
            					JSONObject imgarryobj = new JSONObject();
            					contentstrbf.append(el.text());
            					imgarryobj.put("dtype", "html");
            					imgarryobj.put("content", removeHref(el));
            					contentarry.add(imgarryobj);
        						index++;
        						continue;
        					}
        					arryobj.put("dtype", "html");
        					arryobj.put("content",  removeHref(el));
        					contentarry.add(arryobj);
        					for(Element eimg:el.getElementsByTag("img")){
        						JSONObject imgobj = new JSONObject();
            					imgobj.put("src", eimg.attr("src"));
            					imgobj.put("filepath", filepath);
            					imgarry.add(imgobj);
        					}
        					contentstrbf.append(el.text());
        				}else{
        					if(el.text().contains("精彩回顾")){
        						break;
        					}
        					if(el.text().contains("ID :marriagev")){
        						break;
        					}
        					if(el.text().contains("温馨提示：点击上方\"蓝色字体\"↑关注我们")){
        						continue;
        					}
        					if(el.text().contains("欢迎投稿、")){
        						continue;
        					}
        					if(el.text().contains("相关文章点下面链接")){
        						continue;
        					}
        					contentstrbf.append(el.text());
        					arryobj.put("dtype", "html");
        					arryobj.put("content", removeHref(el));
        					contentarry.add(arryobj);
        					index++;
        				}
        			}
        			Element copyrighte = doc.select("p.yidian-wm-copyright-bottom").first();
        			if(copyrighte!=null){
        				page.putField("copyright", true);
        			}
        			if(imgarry.size()==0){
        				page.putField("viewType", 3+"");
        			}else if(imgarry.size()==1){
        				page.putField("viewType", 2+"");
        			}else if(imgarry.size()>1){
        				page.putField("viewType", 1+"");
        			}
        			page.putField("dutyAuthor", "李琦");
        			page.putField("cdHinfoTypeCd", UrlUtils.getQueryString(url,"cdHinfoType"));
        			page.putField("readPeoOwn", 0);
        			page.putField("colPeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"like")));
        			page.putField("commPeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"commentcount")));
        			page.putField("sharePeoOwn", 0);
        			page.putField("likePeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"up")));
        			page.putField("content", contentarry.toJSONString());
        			page.putField("attAddress", imgarry.toJSONString());
        			
        			if(getChinese(contentarry.toJSONString()).length()<10&&imgarry.size()<1){
        				page.putField("isExists", true);
        				return;
        			}
        			page.putField("contentstrbf", contentstrbf.toString());
        			List<DHinfoBase> dHinfoBasecopys= dHinfoService.selectcopyList(element.select("h2").first().text());
        	        if(dHinfoBasecopys.isEmpty()){
        	        	page.putField("isExists", false);
        	        }else{
						for (DHinfoBase dHinfoBasecopy : dHinfoBasecopys) {
							if (!UrlUtils.isSame(url, dHinfoBasecopy.getHinfoUrl())) {
								DHinfoCon dHinfoConcopy = dHinfoService.selectcontentById(dHinfoBasecopy.getIdHinfo());
								Double result = similarityComparison2(contentarry.toJSONString(),
										dHinfoConcopy.getContent());
								if (result < 0.8) {
									page.putField("isExists", false);
								} else {
									repetitions += 1;
									if (repetitions >= maxRepetitions) {
										if (spider != null) {
											spider.stop();
										}
										spider = null;
									}
									break;
								}
							} else {
								repetitions += 1;
								if (repetitions >= maxRepetitions) {
									if (spider != null) {
										spider.stop();
									}
									spider = null;
								}
								break;
							}

						}
        	        }
        		}else{
        			Element contente = doc.select("div.video-wrapper").first();
        			Element contentevideo = contente.getElementsByTag("video").first();
        			page.putField("src", contentevideo.attr("src"));
        			page.putField("poster", contentevideo.attr("poster"));
        		}
        	}else if (url.startsWith("https://view.bug.cn/yidian/")||url.startsWith("http://t.ynet.cn/yidian")||url.startsWith("http://www.17getfun.com")||url.startsWith("https://t.m.youth.cn")) {
        		if(!"news".equals(UrlUtils.getQueryString(url,"ctype"))){
        			return;
        		}
        		Element heartitle = doc.getElementsByTag("header").first();
				Element titlee = heartitle.select("h1").first();
				if(titlee.text().contains("直播预告")){
        			return;
        		}
				page.putField("hinfoTitle", titlee.text());
				page.putField("hinfoUrl", url);
				Element yidianinfo = heartitle.select("div.yidian-info").first();
				Element datetime = yidianinfo.getElementsByTag("span").get(1);
				try {
    				page.putField("dtmHinfo", DateUtil.toDate(datetime.text(),DateUtil.DATE_FORMAT_CLOOPEN));
				} catch (ParseException e) {
					e.printStackTrace();
					page.putField("dtmHinfo", new Date());
				}
				Element source = yidianinfo.getElementsByTag("span").get(0);
				page.putField("source", source.text());

				Element contente = doc.getElementById("yidian-content");
				Elements contents = contente.getElementsByTag("p");
				JSONArray contentarry = new JSONArray();
				JSONArray imgarry = new JSONArray();
				StringBuffer contentstrbf = new StringBuffer();
				for (Element el : contents) {
					JSONObject arryobj = new JSONObject();
					Element img = el.getElementsByTag("img").first();
					if (img != null) {
						arryobj.put("dtype", "img");
						arryobj.put("src", img.attr("src"));
						contentarry.add(arryobj);
						JSONObject imgobj = new JSONObject();
						imgobj.put("src", img.attr("src"));
						imgobj.put("filepath", filepath);
						imgarry.add(imgobj);
						el.getElementsByTag("img").remove();
						JSONObject imgarryobj = new JSONObject();
						contentstrbf.append(el.text());
						imgarryobj.put("dtype", "html");
						imgarryobj.put("content", removeHref(el));
						contentarry.add(imgarryobj);
					} else {
						if(el.text().contains("精彩回顾")){
    						break;
    					}
    					if(el.text().contains("ID :marriagev")){
    						break;
    					}
    					if(el.text().contains("温馨提示：点击上方\"蓝色字体\"↑关注我们")){
    						continue;
    					}
    					if(el.text().contains("欢迎投稿、")){
    						continue;
    					}
    					if(el.text().contains("相关文章点下面链接")){
    						continue;
    					}
						contentstrbf.append(el.text());
						arryobj.put("dtype", "html");
						arryobj.put("content", removeHref(el));
						contentarry.add(arryobj);
					}
				}
				if (imgarry.size() == 0) {
					page.putField("viewType", 3 + "");
				} else if (imgarry.size() == 1) {
					page.putField("viewType", 2 + "");
				} else if (imgarry.size() > 1) {
					page.putField("viewType", 1 + "");
				}
				page.putField("dutyAuthor", "李琦");
				page.putField("cdHinfoTypeCd", UrlUtils.getQueryString(url, "cdHinfoType"));
				page.putField("readPeoOwn", 0);
    			page.putField("colPeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"like")));
    			page.putField("commPeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"commentcount")));
    			page.putField("sharePeoOwn", 0);
    			page.putField("likePeoOwn", Integer.parseInt(UrlUtils.getQueryString(url,"up")));
				page.putField("content", contentarry.toJSONString());
				page.putField("attAddress", imgarry.toJSONString());

				if(getChinese(contentarry.toJSONString()).length()<10&&imgarry.size()<1){
					page.putField("isExists", true);
    				return;
    			}
				page.putField("contentstrbf", contentstrbf.toString());
				List<DHinfoBase> dHinfoBasecopys= dHinfoService.selectcopyList(titlee.text());
    	        if(dHinfoBasecopys.isEmpty()){
    	        	page.putField("isExists", false);
    	        }else{
					for (DHinfoBase dHinfoBasecopy : dHinfoBasecopys) {
						if (!UrlUtils.isSame(url, dHinfoBasecopy.getHinfoUrl())) {
							DHinfoCon dHinfoConcopy = dHinfoService.selectcontentById(dHinfoBasecopy.getIdHinfo());
							Double result = similarityComparison2(contentarry.toJSONString(),
									dHinfoConcopy.getContent());
							if (result < 0.8) {
								page.putField("isExists", false);
							} else {
								repetitions += 1;
								if (repetitions >= maxRepetitions) {
									if (spider != null) {
										spider.stop();
									}
									spider = null;
								}
								break;
							}
						} else {
							repetitions += 1;
							if (repetitions >= maxRepetitions) {
								if (spider != null) {
									spider.stop();
								}
								spider = null;
							}
							break;
						}
					}
				}
        	}else if(url.startsWith("http://health.enorth.com.cn")){
        		
        		JSONObject contentobj = new JSONObject();
        		
        		
        		Element title = doc.getElementById("title");
        		Element titlee = title.select("h2").first().select("b").first();
        		contentobj.put("hinfo_title", titlee.text());
        		page.putField("title", titlee.text());
        		
        		Element key = doc.select("div.key").first();
        		
        		page.putField("hinfo_summ", key.text());
        		
        		Element contente = doc.select("div.content").first();
    			Elements contents = contente.getElementsByTag("p");
    			JSONArray contentarry = new JSONArray();
    			for(Element el : contents){
    				JSONObject arryobj = new JSONObject();
    				Element img = el.getElementsByTag("img").first();
    				if(img!=null){
    					arryobj.put("dtype", "img");
    					arryobj.put("src", img.attr("src"));
    					contentarry.add(arryobj);
    				}else{
    					arryobj.put("dtype", "text");
    					arryobj.put("content", el.text());
    					contentarry.add(arryobj);
    				}
    			}
    			contentobj.put("content", contentarry);
    			page.putField("jsonobj", contentobj);
        	}else if(url.startsWith("https://m.huanqiu.com/")){
        		
        		JSONObject contentobj = new JSONObject();
        		

        		Element title = doc.select("h1.yidian-title").first();
        		contentobj.put("hinfo_title", title.text());
        		page.putField("title", title.text());

        		
        		Element contente = doc.getElementById("yidian-content");
    			Elements contents = contente.getElementsByTag("p");
    			JSONArray contentarry = new JSONArray();
    			for(Element el : contents){
    				JSONObject arryobj = new JSONObject();
    				Element img = el.getElementsByTag("img").first();
    				if(img!=null){
    					arryobj.put("dtype", "img");
    					arryobj.put("src", img.attr("src"));
    					contentarry.add(arryobj);
    				}else{
    					arryobj.put("dtype", "text");
    					arryobj.put("content", el.text());
    					contentarry.add(arryobj);
    				}
    			}
    			contentobj.put("content", contentarry);
    			page.putField("jsonobj", contentobj);
        	}
    	}
    	
    }  
    
    public static String removeHref(Element content) {
		Elements elements = content.select("a[href]");
		for (Element el : elements) {
			el.removeAttr("href");
		}
		return content.outerHtml();
	}
    
    public static Double similarityComparison(String first , String second){
    	first = first.replaceAll("src([\\w\\W\\s]*?)img", "_");
    	second = second.replaceAll("src([\\w\\W\\s]*?)img", "_");
    	return CosineSimilarAlgorithmUills.cosSimilarityByString(first, second);
    }
    
    public static Double similarityComparison2(String first , String second){
    	first = getChinese(first);
    	second = getChinese(second);
    	return CosineSimilarAlgorithmUills.cosSimilarityByString(first, second);
    }
    
    public static String getChinese(String paramValue) {
		String regex = "([\u4e00-\u9fa5]+)";
		String str = "";
		Matcher matcher = Pattern.compile(regex).matcher(paramValue);
		while (matcher.find()) {
			str += matcher.group(0);
		}
		return str;
	}
    
    public Date convertStrDateTime(String datetime) throws Exception{
		if("前天".equals(datetime)){
			return addTime(new Date(),Calendar.DAY_OF_MONTH,-2);
		}else if("昨天".equals(datetime)){
			return addTime(new Date(),Calendar.DAY_OF_MONTH,-1);
		}else if("今天".equals(datetime)){
			return new Date();
		}else if("明天".equals(datetime)){
			return addTime(new Date(),Calendar.DAY_OF_MONTH,1);
		}else if("后天".equals(datetime)){
			return addTime(new Date(),Calendar.DAY_OF_MONTH,2);
		}else if(CommonUtil.match("[0-9]+?天前", datetime)){
			return addTime(new Date(),Calendar.DAY_OF_MONTH,-getdtetimenumber(datetime));
		}else if(CommonUtil.match("[0-9]+?小时前", datetime)){
			return addTime(new Date(),Calendar.HOUR_OF_DAY,-getdtetimenumber(datetime));
		}else if(CommonUtil.match("[0-9]+?分钟前", datetime)){
			return addTime(new Date(),Calendar.MINUTE,-getdtetimenumber(datetime));
		}else if(CommonUtil.match("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy.MM.dd");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}-[0-9]{2}-[0-9]{2}", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}年[0-9]{2}月[0-9]{2}日", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy年MM月dd日");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}年[0-9]{2}月[0-9]{2}日 [0-9]{2}:[0-9]{2}", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			return sf.parse(datetime);
		}else if(CommonUtil.match("[0-9]{4}年[0-9]{2}月[0-9]{2}日[0-9]{2}时[0-9]{2}分[0-9]{2}秒", datetime)){
			SimpleDateFormat sf = new SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒");
			return sf.parse(datetime);
		}else{
			return new Date(); 
		}
	}
	
	public static Date  addTime(Date date, int calendarField, int amount) {
        if (date == null) {
            throw new IllegalArgumentException("The date must not be null");
        }
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(calendarField, amount);
        return c.getTime();
    }
	
	public static int getdtetimenumber(String dateime) {
		String regex = "([0-9]+)";
		String str = "";
		Matcher matcher = Pattern.compile(regex).matcher(dateime);
		while (matcher.find()) {
			str += matcher.group(0);
		}
		return Integer.parseInt(str);
	}
    
    public void execute(){
    	String url = "http://www.yidianzixun.com/channellist";
    	logger.info("进入爬虫一点资讯，地址："+url);
    	String spiderconfigure = ConfigProperty.getProperty("spider.yidianzixunconfigure");
    	if(null == spiderconfigure){
    		logger.debug("爬虫一点资讯读取配置文件失败！");
    		return;
    	}
    	JSONObject config = JSON.parseObject(spiderconfigure);
    	maxRepetitions = (int) config.getOrDefault("Repetitions", 100);
    	filepath = config.getString("filepath");
    	if(new File(filepath).exists()){
    		if(FileUtil.deleteDir(new File(filepath))){
        		if(!FileUtil.createDir(filepath)){
        			logger.debug("爬虫一点资讯创建临时文件夹失败！");
        		}
        	}else{
        		logger.debug("爬虫一点资讯清理临时文件夹失败！");
        	}
    	}else{
    		if(!FileUtil.createDir(filepath)){
    			logger.debug("爬虫一点资讯创建临时文件夹失败！");
    		}
    	}
    	esindex = ConfigProperty.getProperty("es.hinfo.index0");
    	int threadNum = (int) config.getOrDefault("ThreadNum", 2);
    	if("on".equals(config.getString("switch"))){
    		if(spider!=null){
    			if("Running".equals(spider.getStatus().toString())){
    				logger.debug("爬虫一点资讯正在运行！");
    				return;
    			}else if("Stopped".equals(spider.getStatus().toString())){
    				spider = Spider.create(new yidianzixun()).addUrl(url).addPipeline(new spiderHinfoPipeline()).thread(threadNum);
    				repetitions = 0;
    				spider.start();
    				logger.debug("爬虫一点资讯任务启动！");
    			}
        	}else{
        		spider = Spider.create(new yidianzixun()).addUrl(url).addPipeline(new spiderHinfoPipeline()).thread(threadNum);
				repetitions = 0;
				spider.start();
				logger.debug("爬虫一点资讯任务启动！");
        	}
    	}else if("off".equals(config.getString("switch"))){
    		if(spider!=null){
    			spider.stop();
    			spider = null;
    			repetitions = 0;
    			logger.debug("爬虫一点资讯任务停止！");
        	}
    	}
    }
} 