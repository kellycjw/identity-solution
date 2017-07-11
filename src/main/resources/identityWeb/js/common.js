Date.prototype.customFormat = function(formatString){
  var YYYY,YY,MMMM,MMM,MM,M,DDDD,DDD,DD,D,hhhh,hhh,hh,h,mm,m,ss,s,ampm,AMPM,dMod,th;
  YY = ((YYYY=this.getFullYear())+"").slice(-2);
  MM = (M=this.getMonth()+1)<10?('0'+M):M;
  MMM = (MMMM=["January","February","March","April","May","June","July","August","September","October","November","December"][M-1]).substring(0,3);
  DD = (D=this.getDate())<10?('0'+D):D;
  DDD = (DDDD=["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"][this.getDay()]).substring(0,3);
  th=(D>=10&&D<=20)?'th':((dMod=D%10)==1)?'st':(dMod==2)?'nd':(dMod==3)?'rd':'th';
  formatString = formatString.replace("#YYYY#",YYYY).replace("#YY#",YY).replace("#MMMM#",MMMM).replace("#MMM#",MMM).replace("#MM#",MM).replace("#M#",M).replace("#DDDD#",DDDD).replace("#DDD#",DDD).replace("#DD#",DD).replace("#D#",D).replace("#th#",th);
  h=(hhh=this.getHours());
  if (h==0) h=24;
  if (h>12) h-=12;
  hh = h<10?('0'+h):h;
  hhhh = hhh<10?('0'+hhh):hhh;
  AMPM=(ampm=hhh<12?'am':'pm').toUpperCase();
  mm=(m=this.getMinutes())<10?('0'+m):m;
  ss=(s=this.getSeconds())<10?('0'+s):s;
  return formatString.replace("#hhhh#",hhhh).replace("#hhh#",hhh).replace("#hh#",hh).replace("#h#",h).replace("#mm#",mm).replace("#m#",m).replace("#ss#",ss).replace("#s#",s).replace("#ampm#",ampm).replace("#AMPM#",AMPM);
};


var LOCALHOST = "http://" + location.hostname;
var PORT = window.location.port;
var BASE_URL = LOCALHOST + ":" + PORT + "/api/identity";
var ME_URL = BASE_URL + "/me";
var PEERS_URL = BASE_URL + "/peers";
var CREATE_IDENTITY_URL = BASE_URL + "/create";
var UPDATE_IDENTITY_URL = BASE_URL + "/update";
var DELETE_IDENTITY_URL = BASE_URL + "/delete";
var FIND_CPF_URL = BASE_URL + "/find/";
var UPLOAD_URL = BASE_URL + "/document";
var LIST_IDENTITIES_URL = BASE_URL + "/identities";
var DOWNLOAD_DOCUMENT_URL = BASE_URL + "/document/";

function showSuccessMessage(msg){
    $("#alert_placeholder_success").html('<strong>Success! </strong> ' + msg);
    $("#success-message").alert();
    $("#success-message").show();
    console.log(msg);
}

function showErrorMessage(msg){
    $("#alert_placeholder_error").html('<strong>Error! </strong> ' + msg);
    $("#error-message").alert();
    $("#error-message").show();
    console.log(msg);
}

$(".close").click(function() {
    closeAllMessages();
});

function closeAllMessages(){
    if ($("#error-message")){
        $("#error-message").hide();
    }
    if ($("#success-message")){
        $("#success-message").hide();
    }
}