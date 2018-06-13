package man10homeplugin.man10homeplugin;

import org.bukkit.Location;

/**
 * Created by sho on 2017/10/09.
 */
public class SuperLocation {
    String serverName;
    Location loc;

    SuperLocation(Location l,String serverName){
        this.serverName = serverName;
        this.loc = l;
    }

    public Location getLocation(){
        return loc;
    }

    public String getServerName(){
        return serverName;
    }
}
