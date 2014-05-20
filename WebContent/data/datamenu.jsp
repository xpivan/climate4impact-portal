<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" import="impactservice.* "%>
<div class="impacttopmenu datamenu">

<%

		String Home="/impactportal/";
		//String header=ImpactPages.createHeader(request.getServletPath());
		int numProducts = 0;
		String numProductsString="-";
		
		try{
			numProducts = LoginManager.getUser(request).getShoppingCart().getNumProducts(request);
		}catch(Exception e){				
		}
		if(numProducts!=0){
			numProductsString = ""+numProducts;
		}
				
		String pageName=request.getServletPath();
		boolean highLightHome = false;
		boolean highLightAdvancedSearch = false;
		boolean highLightDownload = false;
		boolean highLightTransform = false;
		boolean highLightUpload = false;
		boolean highLightCustomCatalogs = false;
		boolean highLightCatalogs = false;
		boolean highLightBasket = false;
		boolean highLightBasicSearch = false;
		

		if(pageName.indexOf("data/basicsearch.jsp")!=-1)highLightBasicSearch=true;
		else if(pageName.indexOf("data/advancedsearch.jsp")!=-1)highLightBasicSearch=true;
		else if(pageName.indexOf("data/download.jsp")!=-1)highLightDownload=true;
		else if(pageName.indexOf("data/transform.jsp")!=-1)highLightTransform=true;
		else if(pageName.indexOf("data/wps")!=-1)highLightTransform=true;
		else if(pageName.indexOf("data/upload.jsp")!=-1)highLightUpload=true;
		else if(pageName.indexOf("data/catalogs.jsp")!=-1)highLightCatalogs=true;
		else if(pageName.indexOf("data/catalogbrowser.jsp")!=-1)highLightCatalogs=true;
    else if(pageName.indexOf("data/customcatalog.jsp")!=-1)highLightCustomCatalogs=true;
    else if(pageName.indexOf("data/datasetviewer.jsp")!=-1)highLightCatalogs=true;
		else if(pageName.indexOf("data/basket.jsp")!=-1)highLightBasket=true;

		//out.print(header); //returns file name and path
		%>

  <ul style="height:20px;">
  <!-- 	<li <% if(highLightHome)out.print("class=\"sel\""); %>><a href="<%=Home%>data/basicsearch.jsp" >Overview</a></li>-->
  <li <% if(highLightBasicSearch)out.print("class=\"sel\""); %>><a href="<%=Home%>data/basicsearch.jsp" >Search</a></li>
   <!--  <li <% if(highLightAdvancedSearch)out.print("class=\"sel\""); %>><a href="<%=Home%>data/advancedsearch.jsp" >Advanced Search</a></li> -->
    <li <% if(highLightCatalogs)out.print("class=\"sel\""); %>><a href="<%=Home%>data/catalogs.jsp" >Catalogs</a></li>
    <li <% if(highLightCustomCatalogs)out.print("class=\"sel\""); %>><a href="<%=Home%>data/customcatalog.jsp" >Add catalogs or files</a></li>
    <li <% if(highLightTransform)out.print("class=\"sel\""); %>><a href="<%=Home%>data/transform.jsp" >Processing</a></li>
     <!--<li <% if(highLightBasket)out.print("class=\"sel\""); %>><a href="<%=Home%>data/basket.jsp" >Basket (<%=numProductsString%>)</a></li>-->
    <!-- 
    	<li <% if(highLightUpload)out.print("class=\"sel\""); %>><a href="<%=Home%>data/upload.jsp" >Upload</a></li>
    	
    -->
   
  </ul>  
 </div>
 
 