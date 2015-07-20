package oauth2handling;

import impactservice.Configuration;
import impactservice.Configuration.Oauth2Config.Oauth2Settings;
import impactservice.LoginManager;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.oltu.commons.encodedtoken.TokenDecoder;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import tools.Debug;
import tools.HTTPTools;
import tools.HTTPTools.WebRequestBadStatusException;
import tools.JSONResponse;
import tools.KVPKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
 



/**
 * Class which helps handling OAuth requests using APACHE oltu
 * 
 * @author plieger
 *
 */
public class OAuth2Handler {
  
  /* === Remember to add accounts.google ssl certificate to truststore !!! === 
   
   E.g.:
   echo | openssl s_client -connect accounts.google.com:443 2>&1 | sed -ne
   '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > accounts.google.com
   echo | openssl s_client -connect github.com:443 2>&1 | sed -ne '/-BEGIN
   CERTIFICATE-/,/-END CERTIFICATE-/p' > github.com
   keytool -import -v -trustcacerts -alias accounts.google.com -file
   accounts.google.com -keystore esg-truststore2.ts
   keytool -import -v -trustcacerts -alias github.com -file github.com
   -keystore esg-truststore2.ts* 

   */
   

  /* Restricted URL to check */
  // /impactportal/ImpactService?&source=http://vesg.ipsl.fr/thredds/dodsC/esg_dataroot/CMIP5/output1/IPSL/IPSL-CM5A-LR/1pctCO2/day/atmos/cfDay/r1i1p1/v20110427/albisccp/albisccp_cfDay_IPSL-CM5A-LR_1pctCO2_r1i1p1_19700101-19891231.nc&SERVICE=WMS&&SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=albisccp&WIDTH=1635&HEIGHT=955&CRS=EPSG:4326&BBOX=-105.13761467889908,-180,105.13761467889908,180&STYLES=auto/nearest&FORMAT=image/png&TRANSPARENT=TRUE&&time=1989-11-27T12:00:00Z

  // E.g. wget
  // "http://bhw485.knmi.nl:8280/impactportal/ImpactService?&service=basket&request=getoverview&_dc=1424696174221&node=root"
  // --header="Authorization: Bearer ya29.KwFhgkkEdgQBKh_5eRo_ODoN3h8uvdscC3gbhjcCB46wAWZpSsQg2CjFw8vm5LlygtqYRKQ6esLvuw"
  // -O info.txt --no-check-certificate

  // wget
  // "http://bhw485.knmi.nl:8280/impactportal/ImpactService?&service=basket&request=getoverview&_dc=1424696174221&node=root"
  // --header="Authorization: JWT eyJhbGciOiJSUzI1NiIsImtpZCI6IjlhODEzMzhlMmFmOGVlZjA0ODE5OTA2MzgwZDBkOTZmNjBmNzI4ZjYifQ.eyJpc3MiOiJhY2NvdW50cy5nb29nbGUuY29tIiwic3ViIjoiMTA4NjY0NzQxMjU3NTMxMzI3MjU1IiwiYXpwIjoiMjMxOTMzNTM3NDU2LW1zcjlwOG9zb3VpdTQwMGludmMwbWY4NTdhMTd2NGNxLmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwiZW1haWwiOiJtYWFydGVucGxpZWdlckBnbWFpbC5jb20iLCJhdF9oYXNoIjoiaV9nVzEzc3VpNTRWLWJiTzhHRTlFdyIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJhdWQiOiIyMzE5MzM1Mzc0NTYtbXNyOXA4b3NvdWl1NDAwaW52YzBtZjg1N2ExN3Y0Y3EuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJpYXQiOjE0MjUzMTI3OTEsImV4cCI6MTQyNTMxNjY5MX0.cTmkv5ym0ef6KtLIYQ5DqlD3TSzpbURtrm7qmQAbKcdBtMKxhtuqeXOTd3_pNqRoaoo0vQ5yUv6TLBBlLQTe0MMj0kd5wZqfnjHzeGO0lCu2B8BijDdhYFto1pqzJqWhtluvXuBm0Ws4zZJs5NpkBnXNWMWOW1M04F6hoAfyfao"
  // wget "https://www.googleapis.com/plus/v1/people/me/openIdConnect?"
  // --header="Authorization: Bearer ya29.IwF2DNugbZI1KFo0EUyRMe2o_tfpgAoytDZkT4F0d98azSuGV3N9sTt4JN9zUnLXg3SQykxCz5BOzQ"
  // -O info.txt --no-check-certificate
  // wget
  // "http://bhw485.knmi.nl:8280/impactportal/ImpactService?&service=basket&request=getoverview&_dc=1424696174221&node=root"
  // --header="Authorization: Bearer ya29.IwF2DNugbZI1KFo0EUyRMe2o_tfpgAoytDZkT4F0d98azSuGV3N9sTt4JN9zUnLXg3SQykxCz5BOzQ"
  // --header="Discovery: https://accounts.google.com/.well-known/openid-configuration"
  // -O info.txt --no-check-certificate

  // http://self-issued.info/docs/draft-ietf-oauth-v2-bearer.html#authz-header

  // http://self-issued.info/docs/draft-jones-json-web-token-01.html#DefiningRSA
  // https://www.googleapis.com/oauth2/v2/certs
  // https://console.developers.google.com/project

  static String oAuthCallbackURL = "/oauth"; // The external Servlet location
  
  /**
   * UserInfo object used to share multiple userinfo attributes over functions.
   * @author plieger
   *
   */
  public static class UserInfo{
    public String user_openid = null;
    public String user_identifier = null;
    public String user_email = null;
    public String certificate;
    public String access_token;
    public String certificate_notafter;
  }

  /**
   * Step 1: Starts Oauth2 authentication request. It retrieves a one time
   * usable code which can be used to retrieve an access token or id token
   * 
   * @param httpRequest
   * @return
   * @throws OAuthSystemException
   * @throws IOException
   */
  static void getCode(HttpServletRequest httpRequest,
      HttpServletResponse response) throws OAuthSystemException, IOException {
    LoginManager.logout(httpRequest);

    String provider = null;
    try {
      provider = tools.HTTPTools.getHTTPParam(httpRequest, "provider");
    } catch (Exception e) {
    }
    Debug.println("  OAuth2 Step 1 getCode: Provider is " + provider);

    Configuration.Oauth2Config.Oauth2Settings settings = Configuration.Oauth2Config
        .getOAuthSettings(provider);
    if (settings == null) {
      Debug.errprintln("  OAuth2 Step 1 getCode: No Oauth settings set");
      return;
    }
    Debug.println("  OAuth2 Step 1 getCode: Using " + settings.id);

    OAuthClientRequest oauth2ClientRequest = OAuthClientRequest
        .authorizationLocation(settings.OAuthAuthLoc)
        .setClientId(settings.OAuthClientId)
        .setRedirectURI(Configuration.getHomeURLHTTPS() + oAuthCallbackURL)
        .setScope(settings.OAuthClientScope).setResponseType("code")
        .setState(provider).buildQueryMessage();

    Debug.println("  OAuth2 Step 1 getCode: locationuri = "
        + oauth2ClientRequest.getLocationUri());
    response.sendRedirect(oauth2ClientRequest.getLocationUri());
  }

  /**
   * Step 2: Get authorization response. Here the access_tokens and possibly
   * id_tokens are retrieved with the previously retrieved code.
   * 
   * @param request
   * @param response
   */
  public static void makeOauthzResponse(HttpServletRequest request,
      HttpServletResponse response) {
    try {
      OAuthAuthzResponse oar = OAuthAuthzResponse
          .oauthCodeAuthzResponse(request);

      String stateResponse = oar.getState();
      if (stateResponse == null) {
        stateResponse = "";
      }
      if (stateResponse.equals("")) {
        Debug.errprintln("  OAuth2 Step 2 OAuthz:  FAILED");
        return;
      }

      Debug
          .println("  OAuth2 Step 2 OAuthz:  Provider retrieved from state is " + stateResponse);

      if (request.getParameter("r") != null) {
        Debug.println("  OAuth2 Step 2 OAuthz:  Token request already done, stopping");
        return;
      }

      Debug.println("  OAuth2 Step 2 OAuthz:  Starting token request");

      String currentProvider = stateResponse;

      Configuration.Oauth2Config.Oauth2Settings settings = Configuration.Oauth2Config
          .getOAuthSettings(currentProvider);
      Debug.println("  OAuth2 Step 2 OAuthz:  Using " + settings.id);
      OAuthClientRequest tokenRequest = OAuthClientRequest
          .tokenLocation(settings.OAuthTokenLoc)
          .setGrantType(GrantType.AUTHORIZATION_CODE)
          .setClientId(settings.OAuthClientId).setCode(oar.getCode())
          .setScope(settings.OAuthClientScope)
          .setClientSecret(settings.OAuthClientSecret)
          .setRedirectURI(Configuration.getHomeURLHTTPS() + oAuthCallbackURL)
          .buildBodyMessage();

      OAuthClient oauthclient = new OAuthClient(new URLConnectionClient());
      OAuthAccessTokenResponse oauth2Response = oauthclient
          .accessToken(tokenRequest);

      Debug.println("  OAuth2 Step 2 OAuthz:  Token request succeeded");

      Debug.println("  OAuth2 Step 2 OAuthz:  oauth2Response.getBody():"
          + oauth2Response.getBody());

      Debug.println("  OAuth2 Step 2 OAuthz:  ACCESS TOKEN:" + oauth2Response.getAccessToken());

      handleSpecificProviderCharacteristics(request,settings,oauth2Response);
      
      response.sendRedirect("/impactportal/account/login.jsp");

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  /**
   * All providers are handled a bit different.
   * One of them is CEDA, which offers a certificate issuing service for ESGF.
   * @param request
   * @param settings
   * @param oauth2Response
   */
  private static void handleSpecificProviderCharacteristics(
      HttpServletRequest request, Oauth2Settings settings, OAuthAccessTokenResponse oauth2Response) {
    if (settings.id.equals("ceda")) {
      
      UserInfo userInfo = makeSLCSCertificateRequest(settings.id,oauth2Response.getAccessToken());
      setSessionInfo(request,userInfo);
      
      
    
    }

    if (settings.id.equals("google")) {
      try {

        /* Google */
        String id_token = oauth2Response.getParam("id_token");

        /* Microsoft */
        // String token = oauth2Response.getParam("authentication_token");
        // String token = oauth2Response.getParam("user_id");

        if (id_token == null) {
          Debug.errprintln("ID TOKEN == NULL!");
        }
        if (id_token != null) {
          if (id_token.indexOf(".") != -1) {
            UserInfo userInfo = getIdentifierFromJWTPayload(TokenDecoder.base64Decode(id_token.split("\\.")[1]));
            setSessionInfo(request,userInfo);

            String accessToken = oauth2Response.getAccessToken();
            Debug.println("ACCESS TOKEN:" + accessToken);
            Debug.println("EXPIRESIN:" + oauth2Response.getExpiresIn());

          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
  }

  /**
   * Step 3 - Make SLCS certificate request to external OAuth2 service
   * A - generate keypair
   * B - generate certificate signing request (CSR)
   * C - Request certificate from CEDA service using CSR and access_token
   * D - Retrieve user identifier from retrieved SLCS
   * 
   * @param currentProvider
   * @param accessToken
   * @return
   */
  private static UserInfo makeSLCSCertificateRequest(String currentProvider,String accessToken) {
    Debug.println("Step 3 - Make SLCS certificate request to external OAuth2 service");
    UserInfo userInfo = new UserInfo();
    userInfo.user_identifier = "https://ceda.ac.uk/openid/TODO_howdowegettheidentifier";
    Security.addProvider(new BouncyCastleProvider());

    PublicKey publicKey = null;
    PrivateKey privateKey = null;
    KeyPairGenerator keyGen = null;
    
    //Generate KeyPair
    Debug.println("  Step 3.1 - Generate KeyPair");
    try {
        keyGen = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
        return null;
    }
    keyGen.initialize(2048, new SecureRandom());
    KeyPair keypair = keyGen.generateKeyPair();
    publicKey = keypair.getPublic();
    privateKey = keypair.getPrivate();
    
    //Generate Certificate Signing Request
    Debug.println("  Step 3.2 - Generate CSR");
    String CSRinPEMFormat = null;
    try {
      //PKCS10CertificationRequest a = new PKCS10CertificationRequest("SHA256withRSA", new X500Principal("CN=Requested Test Certificate"), publicKey, null, privateKey);
      PKCS10CertificationRequest a = new PKCS10CertificationRequest("SHA256withRSA", new  X509Name ("CN=Requested Test Certificate"), publicKey, null, privateKey);
      //PemObject pemObject = new PemObject("CERTIFICATE REQUEST", certRequest.getEncoded());
      StringWriter str = new StringWriter();
      PEMWriter pemWriter = new PEMWriter(str);
      pemWriter.writeObject(a);
      pemWriter.close();
      str.close();
      
      CSRinPEMFormat = str.toString();
      //System.out.println(new String(a.getDEREncoded()));
      Debug.println("  CSR Seems OK");
    } catch (InvalidKeyException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (NoSuchProviderException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (SignatureException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    Debug.println("  Step 3.3 - Use SLCS service with CSR and OAuth2 access_token");
    //System.out.println(CSRinPEMFormat);
    
    KVPKey key = new KVPKey();
    key.addKVP("Authorization", "Bearer "+accessToken);
    Debug.println("Starting request");
    
    String postData = null;
    try {
      postData = "certificate_request="+URLEncoder.encode(CSRinPEMFormat,"UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    
    //Debug.println("PostData = ["+postData+"]");
    
    String SLCSX509Certificate = HTTPTools.makeHTTPostRequestWithHeaders("https://slcs.ceda.ac.uk/oauth/certificate/", key,postData);
    
    if(SLCSX509Certificate != null){
      Debug.println("Succesfully retrieved an SLCS\n");
    }
    
    String privateKeyInPemFormat = null;
    try{
      StringWriter str = new StringWriter();
      PEMWriter pemWriter = new PEMWriter(str);
      pemWriter.writeObject(privateKey);
      pemWriter.close();
      str.close();
      privateKeyInPemFormat = str.toString();
    }catch(Exception e){
      
    }
    
//    if(privateKeyInPemFormat != null){
//      Debug.println("Corresponding private key is\n"+privateKeyInPemFormat);
//      
//    }
    
    //Debug.println("ESGF Cert is\n"+SLCSX509Certificate+privateKeyInPemFormat);
    

    
    Debug.println("Finished request");
    
    String CertOpenIdIdentifier = null;
    //org.apache.catalina.authenticator.SSLAuthenticator

    
    X509Certificate cert = null;
    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      cert = (X509Certificate) cf.generateCertificate( new ByteArrayInputStream(SLCSX509Certificate.getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    
    String subjectDN = cert.getSubjectDN().toString();
    Debug.println("getSubjectDN: "+subjectDN);
    String[] dnItems = subjectDN.split(", ");
    for(int j=0;j<dnItems.length;j++){
      int CNIndex = dnItems[j].indexOf("CN");
      if(CNIndex != -1){
        CertOpenIdIdentifier = dnItems[j].substring("CN=".length()+CNIndex);
      }
    }
    userInfo.user_identifier = CertOpenIdIdentifier;
    userInfo.user_openid = CertOpenIdIdentifier;

    userInfo.certificate = SLCSX509Certificate+privateKeyInPemFormat;
    userInfo.access_token = accessToken;
    
    return userInfo;
  }

  /**
   * Sets session parameters for the impactportal
   * @param request
   * @param userInfo
   */
  public static void setSessionInfo(HttpServletRequest request,
      UserInfo userInfo) {
      request.getSession().setAttribute("openid_identifier", userInfo.user_openid);
      request.getSession().setAttribute("user_identifier", userInfo.user_identifier);
      request.getSession().setAttribute("emailaddress", userInfo.user_email);
      request.getSession().setAttribute("certificate", userInfo.certificate);
      request.getSession().setAttribute("access_token", userInfo.access_token);
      request.getSession().setAttribute("login_method", "oauth2");
  }

  /**
   * Verifies a signed JWT Id_token with RSA SHA-256
   * 
   * @param id_token
   * @return true if verified
   * @throws JSONException
   * @throws WebRequestBadStatusException
   * @throws IOException
   * @throws SignatureException
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   */
  @SuppressWarnings("unused")
  private static boolean verify_JWT_IdToken(String id_token)
      throws JSONException, WebRequestBadStatusException, IOException,
      SignatureException, InvalidKeyException, NoSuchAlgorithmException,
      InvalidKeySpecException {
    // http://self-issued.info/docs/draft-jones-json-web-token-01.html#DefiningRSA
    // The JWT Signing Input is always the concatenation of a JWT Header
    // Segment, a period ('.') character, and the JWT Payload Segment
    // RSASSA-PKCS1-V1_5-VERIFY

    // 8.2. Signing a JWT with RSA SHA-256
    //
    // This section defines the use of the RSASSA-PKCS1-v1_5 signature algorithm
    // as defined in RFC 3447 [RFC3447], Section 8.2 (commonly known as PKCS#1),
    // using SHA-256 as the hash function. Note that the use of the
    // RSASSA-PKCS1-v1_5 algorithm is described in FIPS 186-3 [FIPS.186‑3],
    // Section 5.5, as is the SHA-256 cryptographic hash function, which is
    // defined in FIPS 180-3 [FIPS.180‑3]. The reserved "alg" header parameter
    // value "RS256" is used in the JWT Header Segment to indicate that the JWT
    // Crypto Segment contains an RSA SHA-256 signature.
    //
    // A 2048-bit or longer key length MUST be used with this algorithm.
    //
    // The RSA SHA-256 signature is generated as follows:
    //
    // Let K be the signer's RSA private key and let M be the UTF-8
    // representation of the JWT Signing Input.
    // Compute the octet string S = RSASSA-PKCS1-V1_5-SIGN (K, M) using SHA-256
    // as the hash function.
    // Base64url encode the octet string S, as defined in this document.
    //
    // The output is placed in the JWT Crypto Segment for that JWT.
    //
    // The RSA SHA-256 signature on a JWT is validated as follows:
    //
    // Take the JWT Crypto Segment and base64url decode it into an octet string
    // S. If decoding fails, then the token MUST be rejected.
    // Let M be the UTF-8 representation of the JWT Signing Input and let (n, e)
    // be the public key corresponding to the private key used by the signer.
    // Validate the signature with RSASSA-PKCS1-V1_5-VERIFY ((n, e), M, S) using
    // SHA-256 as the hash function.
    // If the validation fails, the token MUST be rejected.
    //
    // Signing with the RSA SHA-384 and RSA SHA-512 algorithms is performed
    // identically to the procedure for RSA SHA-256 - just with correspondingly
    // longer key and result values.

    Debug.println("Starting verification of id_token");
    Debug.println("[" + id_token + "]");
    String JWTHeader = TokenDecoder.base64Decode(id_token.split("\\.")[0]);
    String JWTPayload = TokenDecoder.base64Decode(id_token.split("\\.")[1]);
    String JWTSigningInput = id_token.split("\\.")[0] + "."
        + id_token.split("\\.")[1];
    String JWTSignature = id_token.split("\\.")[2];

    Debug.println("Decoded JWT IDToken Header:" + JWTHeader);
    Debug.println("Decoded JWT IDToken Payload:" + JWTPayload);

    // Find the discovery page
    JSONObject JWTPayLoadObject = (JSONObject) new JSONTokener(JWTPayload)
        .nextValue();
    String iss = JWTPayLoadObject.getString("iss");
    Debug.println("iss=" + iss);

    // Load the OpenId discovery page
    String discoveryURL = "https://" + iss
        + "/.well-known/openid-configuration";
    JSONObject openid_configuration = (JSONObject) new JSONTokener(
        HTTPTools.makeHTTPGetRequest(discoveryURL)).nextValue();
    String jwks_uri = openid_configuration.getString("jwks_uri");
    Debug.println("jwks_uri:" + jwks_uri);

    // Load the jwks uri
    JSONObject certs = (JSONObject) new JSONTokener(
        HTTPTools.makeHTTPGetRequest(jwks_uri)).nextValue();
    JSONArray jwks_keys = certs.getJSONArray("keys");
    Debug.println("jwks_keys:" + jwks_keys.length());

    JSONObject JWTHeaderObject = (JSONObject) new JSONTokener(JWTHeader)
        .nextValue();
    String kid = JWTHeaderObject.getString("kid");
    Debug.println("kid=" + kid);

    String modulus = null;
    String exponent = null;

    for (int j = 0; j < jwks_keys.length(); j++) {
      if (jwks_keys.getJSONObject(j).getString("kid").equals(kid)) {
        Debug.println("Found kid in jwks");
        modulus = jwks_keys.getJSONObject(j).getString("n");
        exponent = jwks_keys.getJSONObject(j).getString("e");
        break;
      }
    }
    return RSASSA_PKCS1_V1_5_VERIFY(modulus, exponent, JWTSigningInput,
        JWTSignature);
  }

  /**
   * RSASSA-PKCS1-V1_5-VERIFY ((n, e), M, S) using SHA-256
   * 
   * @param modulus_n
   * @param exponent_e
   * @param signinInput_M
   * @param signature_S
   * @return
   * @throws SignatureException
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws InvalidKeySpecException
   */
  static boolean RSASSA_PKCS1_V1_5_VERIFY(String modulus_n, String exponent_e,
      String signinInput_M, String signature_S) throws SignatureException,
      InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException {
    Debug.println("Starting verification");
    /* RSA SHA-256 RSASSA-PKCS1-V1_5-VERIFY */
    // Modulus (n from https://www.googleapis.com/oauth2/v2/certs)
    String n = modulus_n;
    // Exponent (e from https://www.googleapis.com/oauth2/v2/certs)
    String e = exponent_e;
    // The JWT Signing Input (JWT Header and JWT Payload concatenated with ".")
    byte[] M = signinInput_M.getBytes();
    // Signature (JWT Crypto)
    byte[] S = Base64.decodeBase64(signature_S);

    byte[] modulusBytes = Base64.decodeBase64(n);
    byte[] exponentBytes = Base64.decodeBase64(e);
    BigInteger modulusInteger = new BigInteger(1, modulusBytes);
    BigInteger exponentInteger = new BigInteger(1, exponentBytes);

    RSAPublicKeySpec rsaPubKey = new RSAPublicKeySpec(modulusInteger,
        exponentInteger);
    KeyFactory fact = KeyFactory.getInstance("RSA");
    PublicKey pubKey = fact.generatePublic(rsaPubKey);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(pubKey);
    signature.update(M);
    boolean isVerified = signature.verify(S);
    Debug.println("Verify result [" + isVerified + "]");
    return isVerified;
  }

  /**
   * Check if an access token was provided in the HttpServletRequest object and return a user identifier on success.
   * 
   * It returns the unique user identifier. It does this by calling the userinfo_endpoint using the access_token. 
   * All endpoints are discovered by reading the open-id Discovery service.
   * This is one of the OpenId-Connect extensions on OAuth2
   * 
   * @param request
   * @return
   * @throws JSONException
   * @throws WebRequestBadStatusException
   * @throws IOException
   */
  public static UserInfo verifyAndReturnUserIdentifier(HttpServletRequest request)
      throws JSONException, WebRequestBadStatusException, IOException {

    //1) Find the Authorization header containing the access_token
    String access_token = request.getHeader("Authorization");
    if (access_token == null){
      //No access token, probably not an OAuth2 request, skip.
      return null;
    }
    Debug.println("Authorization    : " + access_token);
    
    //2) Find the Discovery service, it might have been passed in the request headers:
    String discoveryURL = request.getHeader("Discovery");
    if (discoveryURL == null) {
      discoveryURL = "https://accounts.google.com/.well-known/openid-configuration";
    }
    Debug.println("Discovery        : " + discoveryURL);
    
    //3 Retrieve the Discovery service, so we get all service endpoints
    String discoveryData = HTTPTools.makeHTTPGetRequest(discoveryURL);
    JSONObject jsonObject = (JSONObject) new JSONTokener(discoveryData)
        .nextValue();
    
    //4) Retrieve userinfo endpoint
    String userInfoEndpoint = jsonObject.getString("userinfo_endpoint");
    Debug.println("userInfoEndpoint:" + userInfoEndpoint);

    //5) Make a get request with Authorization headers set, the access_token is used here as Bearer.
    KVPKey key = new KVPKey();
    key.addKVP("Authorization", access_token);
    Debug.println("Starting request");
    String id_token = HTTPTools.makeHTTPGetRequestWithHeaders(userInfoEndpoint, key);// ,"Authorization: Bearer "+access_token);
    Debug.println("Finished request");

    //6) The ID token is retrieved, now return the identifier from this token.
    Debug.println("Retrieved id_token=" + id_token);
    return getIdentifierFromJWTPayload(id_token);
  }

  
  
  
  /**
   * Returns unique user identifier from id_token (JWTPayload). The JWT token is *NOT* verified.
   * Several impact portal session attributes are set:
   * - user_identifier
   * - emailaddress
   * 
   * @param request
   * @param JWT
   * @return
   */
  private static UserInfo getIdentifierFromJWTPayload(String JWT) {
    JSONObject id_token_json = null;
    try {
      id_token_json = (JSONObject) new JSONTokener(JWT).nextValue();
    } catch (JSONException e1) {
      Debug.errprintln("Unable to convert JWT Token to JSON");
      return null;
    }

    String email = "null";
    String userSubject = null;
    String aud = "";
    try {
      email = id_token_json.get("email").toString();
    } catch (JSONException e) {
    }
    try {
      userSubject = id_token_json.get("sub").toString();
    } catch (JSONException e) {
    }

    try {
      aud = id_token_json.get("aud").toString();
    } catch (JSONException e) {
    }

    if (aud == null)
      return null;
    if (userSubject == null)
      return null;
    String user_identifier = aud + "/" + userSubject;
    String user_openid = null;
    UserInfo userInfo = new UserInfo();
    userInfo.user_identifier = user_identifier;
    userInfo.user_openid = user_openid;
    userInfo.user_email = email;
    


    Debug.println("getIdentifierFromJWTPayload: Found unique ID" + user_identifier);

    return userInfo;

  }

  /**
   * Endpoint which should directly be called by the servlet.
   * 
   * @param request
   * @param response
   */
  public static void doGet(HttpServletRequest request,
      HttpServletResponse response) {

    //Check if we are dealing with getting JSON request for building up the login form
    String makeform = null;
    try {
      makeform = tools.HTTPTools.getHTTPParam(request, "makeform");
    } catch (Exception e) {
    }
    if(makeform != null){
      makeForm(request,response);
      return;
    }
    
    //Check if we are dealing with step1 or step2 in the OAuth process.
    String code = null;
    try {
      code = tools.HTTPTools.getHTTPParam(request, "code");
    } catch (Exception e) {
    }

    if (code == null) {
      //Step 1
      Debug.println("Step 1: start GetCode request for " + request.getQueryString());
      try {
        getCode(request, response);
      } catch (OAuthSystemException e1) {
        e1.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      //Step 2
      Debug.println("Step 2: start makeOauthzResponse for " + request.getQueryString());
      makeOauthzResponse(request, response);

    }

  }

  /**
   * Makes a JSON object and sends it to response with information needed for building the OAuth2 login form.
   * @param request
   * @param response
   */
  private static void makeForm(HttpServletRequest request, HttpServletResponse response) {
    JSONResponse jsonResponse = new JSONResponse();

    JSONObject form = new JSONObject();
    try {
      
      JSONArray providers = new JSONArray();
      form.put("providers", providers);
      Vector<String> providernames = Configuration.Oauth2Config.getProviders();
      
      for(int j=0;j<providernames.size();j++){
        Configuration.Oauth2Config.Oauth2Settings settings = Configuration.Oauth2Config.getOAuthSettings( providernames.get(j));
        JSONObject provider = new JSONObject();
        provider.put("id", providernames.get(j));
        provider.put("description", settings.description);
        provider.put("logo", settings.logo);
        provider.put("registerlink", settings.registerlink);
        providers.put(provider);
        
      }
    } catch (JSONException e) {
    }
    jsonResponse.setMessage(form);
    
    try {
      jsonResponse.setJSONP(request);
      response.setContentType(jsonResponse.getMimeType());
      response.getOutputStream().print(jsonResponse.getMessage());
    } catch (Exception e1) {
    
    }
    
  }

}
