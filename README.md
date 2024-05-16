# Matrix terminal bot

This bot will start a program and send all of its output on standard output or standard error to a Matrix room, and pipe all messages from that Matrix room to its standard input. This lets you use many terminal programs directly from Matrix, although programs that send ANSI escape sequences like Ncurses programs will not work very well yet. [Edbrowse](https://edbrowse.org) works very well under this bot, allowing you to browse the web, edit files, use email, and access IRC from a Matrix room.

You can even run bash and access a full shell over Matrix, although the prompt will not be sent to the room. Other shell-like programs, such as REPLs like Python or Racket, or bc, dc, and text adventure games should also work.

## Setup

### Obtaining an Access Token

First you need to create a Matrix account for the bot and obtain a token. You can either create an account on a third-party homeserver such as matrix.org, or register an account on a homeserver you host yourself. To register an account on your own Synapse server, run `register_new_matrix_user -c /etc/synapse/homeserver.yaml` (adjust the path to your Synapse configuration file if necessary).

After you have an account for the bot, follow [these directions](https://t2bot.io/docs/access_tokens/) to obtain an access token. However, do not close your browser yet since you still need it to complete the remaining steps.

### Joining a Room

Either search for a room and join it from Element, or invite the bot to a room from your main account and accept the invite. If you want to use this bot on another messaging service like Discord, you could also set up an account on that service and bridge to it using the bot's Matrix account. For Discord, it is recommended to create a bot account, and instructions for doing this with mautrix-discord can be found [here](https://docs.mau.fi/bridges/go/discord/authentication.html). After you login to the bridge with your third-party account, make sure it creates the room for the chat you want the bot to use.

Then open the room you have joined, and copy the string after and including the exclamation mark in your browser's address bar. This is the room ID of this room.

### Installing the Bot on Linux

To install the bot on Linux, first create a directory for it and copy the files from this repository there. For example, run:

```
git clone https://github.com/emassey0135/matrix-terminal-bot
sudo cp -r matrix-terminal-bot /opt/
```

Then create a user for it. There is a sysusers.d file in this repository, so you can simply run:

```
sudo cp matrix-terminal-bot.conf /etc/sysusers.d/
sudo systemd-sysusers
```

Next, change the ownership of the directory where you installed the bot so its user can access it and save its data:

```
sudo chown -R matrix-terminal-bot:matrix-terminal-bot /opt/matrix-terminal-bot
```

Install the NPM modules the bot uses. Make sure to do this as the bot user.

```
cd /opt/matrix-terminal-bot
sudo -u matrix-terminal-bot npm install
```

Compile the bot with shadow-cljs. Currently, building in release mode is not supported, so compile it in debug mode.

```
sudo -u matrix-terminal-bot npx shadow-cljs compile bot
```

Open matrix-terminal-bot.service in a text editor and fill in the MATRIX_TOKEN, MATRIX_SERVER_URL, MATRIX_ROOM_ID, and PROGRAM environment variables. The PROGRAM variable should contain the relative or absolute path to the program or script you would like the bot to launch.

You can also uncomment and fill in the MAX_MESSAGE_LENGTH variable if you will be using the bot with another messaging service like Discord. For Discord, the maximum message length is 2000 characters, but it is recommended to set this variable to 1996 to leave extra space for the HTML tags the bot inserts.

Finally install, enable, and start the systemd service:

```
sudo cp matrix-terminal-bot.service /etc/systemd/system/
sudo systemctl enable --now matrix-terminal-bot
```

If you set up everything correctly, the bot should start relaying messages between the Matrix room and the terminal program.

### Testing the Bot or Running on Other Operating Systems

If you wish to test the bot before installing it, or you are not on Linux, you can run the bot directly with Node.js. For example:

```
git clone https://github.com/emassey0135/matrix-terminal-bot
cd matrix-terminal-bot
npm install
npx shadow-cljs compile bot
MATRIX_TOKEN=your_token MATRIX_SERVER_URL=https://matrix.org MATRIX_ROOM_ID=!xxxxx:your.domain PROGRAM=/usr/bin/edbrowse node out/matrix-terminal-bot.js
```

I have only tested it on Linux, but it should run on any platform supported by Node.js.

## Configuration

This program takes its configuration from environment variables. These are the variables it currently accepts.

|Variable|Description|
|---|------|
|MATRIX_TOKEN|The Matrix access token for the account you want the bot to use|
|MATRIX_SERVER_URL|The URL of your Matrix homeserver, including the protocol. The bot will not follow .well-known files, so make sure the URL is actually where Matrix is accessible at.|
|MATRIX_ROOM_ID|The room ID of the room the bot will monitor and send messages in. The bot account must already be joined to this room. A room alias (such as "#room:matrix.org") will not work.|
|PROGRAM|The relative or absolute path to the program the bot will launch. You can also put a path to an executable script here.|
|MAX_MESSAGE_LENGTH|The maximum message length the bot will send. This is useful for using the bot with services like Discord that have more limited message lengths than Matrix. If this variable is not set, the bot will use 64000.|

## Behavior and Other Considerations

* The bot will send standard error to the room in the same way it sends standard output.
* If the program exits, the bot will send a message with the exit code or signal that terminated the program, and then restart it.
* Currently, the bot does not support using more than one room or starting more than one program, so you will have to run multiple instances of the bot for this.
* If a message fails to send due to rate limiting, the bot will retry it after waiting 100 ms (and then twice as long until it succeeds)

## Bugs

This bot has not been widely tested, so it is likely to still have bugs. If you find a but, create an issue in this repository.

## Security Considerations

The program the bot launches will run as the same user as the bot, and will have access to anything the bot does. Therefore, be careful before you put this bot in a room other people have access too, since they can use the program just as easily as you can.
