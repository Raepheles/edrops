# eDrops

Utility bot for the browser game [e-sim](http://e-sim.org). It has one simple function where it calculates the amount of equipment drops that battle will give. Usage is simple; type the prefix, server name, empty space, battle id.

Considering bot prefix is '!' and we want to see drops of the battle with id 100 in alpha server this is the command you should use: `!alpha 100`.

If there is a drop bonus going on simply add drop amount at the end. 120% bonus drop example: `!alpha 100 120`.

If you want to run the bot yourself first edit `config.properties.example` file's name to `config.properties` then edit the file according to your needs. Afterwards you can simply package the application using `mvn package` command and run the jar file in the target location.

I am currently running this bot on my host if you want you can just use [this link](https://discordapp.com/oauth2/authorize?client_id=563020477627629568&permissions=18432&scope=bot) to invite it to your server.
