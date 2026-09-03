# Setting the update server up

Root is needed once, for the vhost and the folder. Everything after that is
copying files as the ordinary user.

```sh
sudo mkdir -p /var/www/updates
sudo chmod 777 /var/www/updates

sudo cp ~/PRJ/updater/server/updates.conf /etc/apache2/sites-available/updates.conf
sudo a2enmod headers
sudo a2ensite updates
sudo apache2ctl configtest && sudo systemctl reload apache2
```

`a2enmod headers` is not optional: `mod_headers` is not enabled on this machine,
and Apache refuses to start on the `Header` directive without it.

Check it answers, from this machine and then from the phone's browser:

```sh
curl -sS http://192.168.54.250:8081/
```

The folder is 777, so publishing needs no root:

```sh
mkdir -p /var/www/updates/reminderlists
```

## If the phone cannot reach it

- `sudo ufw allow 8081/tcp` — the firewall, if one is running.
- The app must carry `updater_network_security_config.xml`, or Android blocks
  cleartext before the request leaves the phone.
