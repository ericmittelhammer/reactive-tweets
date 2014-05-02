var RT = {

    template : function(){},

    prependListItem : function(listItemHTML){
        $(listItemHTML)
            .hide()
            .css('opacity',0.0)
            .prependTo('#messages')
            .slideDown('fast')
            .animate({opacity: 1.0});
    },

    connect: function() {
        var name = $('#inputName').val();
        var socket = new WebSocket('ws://localhost:9000/socket?name=' + name);

        socket.onmessage = function(event) {
            var message = JSON.parse(event.data);
            console.log(message);
            console.log(RT.template);
            var html = RT.template(message);
            RT.prependListItem(html);
        };
    }
};

$(document).ready( function() {
    var source   = $("#message-template").html();
    RT.template = Handlebars.compile(source);
    console.log(RT.template);
});