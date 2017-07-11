$(function () {
    $.get("headerTop.html", function (data) {

        $('body').prepend($(data));

        $.ajax({
             type: 'GET',
             url: ME_URL,
             success: (result) => {
                 $("#titleHeader").text("Identity - " + result.me);
//                 if(result.me.toLowerCase().indexOf("bvmf") == -1){
//                     $("#createMenu").hide();
//                 }
             },
             error: function (cb) { console.log(cb); }
        });
    });
});