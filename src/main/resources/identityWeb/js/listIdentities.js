$( document ).ready(function() {
    listIdentities();
});

 function listIdentities(){
    var newRowContent
    var identity
    $.ajax({
        type: 'GET',
        url: LIST_IDENTITIES_URL,
        success: (result) => {
            result.forEach(function(item, index) {
                identity = item.state.data.identity

                var anexosAppend = "N.A.";

                if(identity.documents){
                    anexosAppend = "";
                    identity.documents.forEach(function(document){
                        anexosAppend += "<a href='" + DOWNLOAD_DOCUMENT_URL + document.secureHash + "/" + document.name + "'>Download</a><br/>";
                    });
                }

                newRowContent = "<tr>"
                newRowContent += "<td>" + identity.name + "</td>"
                newRowContent += "<td>" + identity.idNo + "</td>"
                newRowContent += "<td>" + identity.passportNo + "</td>"
                newRowContent += "<td>" + anexosAppend + "</td>";
                newRowContent += "<td>"
                newRowContent += "<a href='updateIdentity.html?cpf=" + identity.idNo + "'>Update</a> | "
                newRowContent += "<a href='deleteIdentity.html?cpf=" + identity.idNo + "'>Delete</a>"
                newRowContent += "</td>"
                newRowContent += "</tr>"

                $(newRowContent).appendTo($("#tbl_identities"));
            });
        },
        error: (errorResult) => {
            showErrorMessage(errorResult);
        }
    });
 }
