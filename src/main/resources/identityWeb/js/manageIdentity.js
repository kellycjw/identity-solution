$(document).ready(function() {
    $("#dataNascimento").datepicker({ dateFormat: 'dd/mm/yy' });
    updatePeers();
 });

$("#findByCpf").click(function() {
    closeAllMessages();
    $("#hiddenFields").hide();
    cleanUpdateFields();
    cleanFieldClasses();

    var cpfValue = $("#cpfFind").val();
    if(!cpfValue){
        $("#cpf-div").removeClass("has-error").addClass("has-error");
        showErrorMessage("Please enter the identification no.");
    } else {
        $.ajax({
           type: 'GET',
           url: FIND_CPF_URL + cpfValue,
           success: (result) => {

                if(result.documents){
                    result.documents.forEach(function(document) {
                        var newRowContent = "<tr>" +
                                                "<td>" + document.name + "</td>" +
                                                "<td> <a href='" + DOWNLOAD_DOCUMENT_URL + document.secureHash + "/" + document.nome + "'>Download</a></td>" +
                                            "</tr>";
                        $(newRowContent).appendTo($("#tbl_arquivos"));
                    });
                }

                $("#nome").val(result.name);
                $("#cpf").val(result.idNo);
                $("#endereco").val(result.address);
                $("#telefone").val(result.phoneNo);
                $("#email").val(result.email);
                $("#documento").val(result.passportNo);

                var nascimento = result.dob //.split("-");//1970-01-24
                $("#dataNascimento").val(nascimento[2] + "/" + nascimento[1] + "/" + nascimento[0]);

                $.each($("input[name='participants']"), function() {
                    var inputValue = $(this).val();
                    var el = $(this);
                    result.participants.forEach(function(participant) {
                        if(inputValue.toLowerCase() == participant.toLowerCase()){
                           $(el).attr('checked', true);
                       }
                    });
                });

                $("#hiddenFields").show();
           },
           error: (errorResult) => {
                showErrorMessage(errorResult.responseText);
           }
       });
    }
});

$("#update").click(function (){
    closeAllMessages();
    cleanFieldClasses();
    go("POST", UPDATE_IDENTITY_URL, "Identity successfully updated in the ledger. ");
});

$("#new").click(function (){
    closeAllMessages();
    cleanFieldClasses();
    go("PUT", CREATE_IDENTITY_URL, "Identity successfully created in ledger.");
});

$("#delete").click(function (){
    closeAllMessages();
    if( confirm('Are you sure you want to delete this identity?')==false ) return;
    cleanFieldClasses();
    go("POST", DELETE_IDENTITY_URL, "Identity successfully deleted in ledger. ");
});

function cleanUpdateFields(){
    $("#tbl_arquivos tbody").empty();
    $("#participantsCheckbox tbody").empty();
}

function updateAttachments(){
    $("updateFiles").show();
}

function go(method, url, successMessage){
    var cpfValue = $("#cpf").val();
    var nomeValue = $("#nome").val();
    var dobValue = $("#dataNascimento").val();
    var emailValue = $("#email").val();
    var documentoValue = $("#documento").val();

    $("#cpf-div").removeClass("has-error");
    $("#nome-div").removeClass("has-error");
    $("#nascimento-div").removeClass("has-error");
    $("#email-div").removeClass("has-error");
    $("#documento-div").removeClass("has-error");

    if(!cpfValue)       $("#cpf-div").addClass("has-error");
    if(!nomeValue)      $("#nome-div").addClass("has-error");
    if(!dobValue)       $("#nascimento-div").addClass("has-error");
    if(!emailValue)     $("#email-div").addClass("has-error");
    if(!documentoValue) $("#documento-div").addClass("has-error");

    if(!cpfValue || !nomeValue || !dobValue || !emailValue || !documentoValue) {
        showErrorMessage("Please fill in all the required fields: Identification No., Name, Date of Birth, Passport No., and Email.");
    } else {
        var fileName = $("#file").val();
        var idx;
        var attachments = [];

        if(fileName && (idx = fileName.lastIndexOf("\\"))>-1){
            var data = new FormData();
            $.each($('#file')[0].files, function(i, file) {
                data.append('file-'+i, file);
            });

            //calls ajax post to upload the document
            $.ajax({
                url: UPLOAD_URL,
                type: "POST",
                data: data,
                contentType: false,
                processData: false,
                complete: function(result) {
                    if(result.status == "200"){
                        var secureHash = result.responseText;
                        console.log("Document uploaded. Hash 1: " + secureHash)
                        fileName = fileName.substr(idx+1);

                        //Create an json object for the document
                        var document = { name: fileName, secureHash: secureHash, active: true }
                        attachments.push(document);

                        handleIdentityManagement(method, url, successMessage, attachments);
                    } else {
                        showErrorMessage(result.responseText);
                        return;
                    }
                }
            });
        } else {
            handleIdentityManagement(method, url, successMessage, attachments);
        }
    }
}

 function updatePeers(){
    //Retrieves which nodes are online, so that the client can choose who they want to share the data with ...
    $.ajax({
        type: 'GET',
        url: PEERS_URL,
        dataType: "json",
        success: (result) => {
            result.peers.forEach(function(eachPeer, index) {
                $('<input />', {
                    type : 'checkbox',
                    name: 'participants',
                    value: eachPeer
                }).appendTo("#participantsCheckbox");
                $("<span>&nbsp;</span><label for='" + 'id' + eachPeer + "'>"+ eachPeer + " &nbsp;</label><br />").appendTo("#participantsCheckbox");
            });

            //if there is a value for cpf
            if((cpf = getParameterByName('cpf')) != null){
                //then let's trigger the api to search for identity by cpf
                $("#cpfFind").val(cpf)
                $("#findByCpf").click();
            }
        },
        error: function (cb) { cb }
    });
 }

function handleIdentityManagement(method, url, successMessage, attachments) {

    var birthDate = $("#dataNascimento").val().split("/");

    var jsonObj = {
          idNo: $("#cpf").val(),
          name: $("#nome").val(),
          address: $("#endereco").val(),
          phoneNo: $("#telefone").val(),
          email: $("#email").val(),
          passportNo: $("#documento").val(),
          participants: $("#participantsCheckbox input:checkbox:checked").map(function(){return $(this).val().toLowerCase()}).get(),
          dob: birthDate[2] + "-" + ("0" + birthDate[1]).slice(-2) + "-" +  ("0" + birthDate[0]).slice(-2),
          documents: attachments
    };

    $.ajax({
        type: method,
        url: url,
        data: JSON.stringify(jsonObj),
        dataType: "json",
        contentType: "application/json; charset=utf-8",
        complete: function(result) {
            if (result.status == "201" || result.status == "200"){
                if (url == UPDATE_IDENTITY_URL) {
                    document.getElementById("file").value = "";
                    //if there is a new document being uploaded
                    if(result.responseJSON.documents.length > 0){
                        $("#tbl_arquivos").find("tr:gt(0)").remove();
                        result.responseJSON.documents.forEach(function(document) {
                            var newRowContent = "<tr>" +
                                                    "<td>" + document.name + "</td>" +
                                                    "<td> <a href='" + DOWNLOAD_DOCUMENT_URL + document.secureHash + "/" + document.name + "'>Download</a></td>" +
                                                "</tr>";
                            $(newRowContent).appendTo($("#tbl_arquivos"));
                        });
                    }
                } else if (url == DELETE_IDENTITY_URL) {
                    $('#hiddenFields').hide();
                    $('#cpfFind').val("");
                }
                showSuccessMessage(successMessage);
            } else {
                showErrorMessage(result.responseText);
            }
        }
    });
}

function cleanFieldClasses(){
    $("cpf-div").removeClass("has-error");
    $("nome-div").removeClass("has-error");
    $("endereco-div").removeClass("has-error");
    $("telefone-div").removeClass("has-error");
    $("email-div").removeClass("has-error");
    $("documento-div").removeClass("has-error");
    $("nascimento-div").removeClass("has-error");
}

function getParameterByName(name, url){
    if (!url) {
      url = window.location.href;
    }
    name = name.replace(/[\[\]]/g, "\\$&");
    var regex = new RegExp("[?&]" + name + "(=([^&#]*)|&|#|$)"),
        results = regex.exec(url);
    if (!results) return null;
    if (!results[2]) return '';
    return decodeURIComponent(results[2].replace(/\+/g, " "));
}