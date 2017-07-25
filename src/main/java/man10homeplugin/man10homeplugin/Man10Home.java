package man10homeplugin.man10homeplugin;

import jdk.nashorn.internal.ir.Block;
import man10vaultapi.vaultapi.VaultAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.omg.CORBA.INTERNAL;
import red.man10.Man10PlayerDataArchive.Man10PlayerData;
import red.man10.Man10PlayerDataArchive.Man10PlayerDataArchiveAPI;
import red.man10.man10mysqlapi.MySQLAPI;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

public final class Man10Home extends JavaPlugin implements Listener {

    ItemStack defaultIcon = new ItemStack(Material.BED,1,(short) 14);
    
    String prefix = "§e§l[§d§lMan10Home§e§l]§d§l";
    MySQLAPI mysql = null;
    VaultAPI vault = null;

    FileConfiguration config = null;

    int freeSlot = 0;

    String createHomInfo = "CREATE TABLE `home_info` (\n" +
            "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
            "\t`user` VARCHAR(50) NULL DEFAULT '0',\n" +
            "\t`uuid` VARCHAR(50) NULL DEFAULT '0',\n" +
            "\t`slot` INT NULL DEFAULT '0',\n" +
            "\t`icon` VARCHAR(128) NULL DEFAULT '0',\n" +
            "\t`damage` INT NULL DEFAULT '0',\n" +
            "\t`name` VARCHAR(128) NULL DEFAULT '0',\n" +
            "\t`world` VARCHAR(64) NULL DEFAULT '0',\n" +
            "\t`x` DOUBLE NULL DEFAULT '0',\n" +
            "\t`y` DOUBLE NULL DEFAULT '0',\n" +
            "\t`z` DOUBLE NULL DEFAULT '0',\n" +
            "\t`pitch` DOUBLE NULL DEFAULT '0',\n" +
            "\t`yaw` DOUBLE NULL DEFAULT '0',\n" +
            "\t`date_time` DATETIME NULL DEFAULT NULL,\n" +
            "\t`time` BIGINT NULL DEFAULT '0',\n" +
            "\t PRIMARY KEY (`id`)\n" +
            ")\n" +
            "COLLATE='utf8_general_ci'\n" +
            "ENGINE=InnoDB\n" +
            ";\n";

    String createHomeLog = "CREATE TABLE `home_log` (\n" +
            "\t`id` INT NOT NULL AUTO_INCREMENT,\n" +
            "\t`name` VARCHAR(50) NULL,\n" +
            "\t`uuid` VARCHAR(50) NULL,\n" +
            "\t`action` VARCHAR(50) NULL,\n" +
            "\t`value` VARCHAR(50) NULL,\n" +
            "\t`date_time` DATETIME NULL,\n" +
            "\t`time` BIGINT NULL,\n" +
            "\t PRIMARY KEY (`id`)\n" +
            ")\n" +
            "COLLATE='utf8_general_ci'\n" +
            "ENGINE=InnoDB\n" +
            ";\n";


    Location defaultLocation = null;
    List<String> forbiddenWorld = new ArrayList<>();

    @Override
    public void onEnable() {
        boot();
    }
    void boot(){
        // Plugin startup logic
        config = this.getConfig();
        mysql = new MySQLAPI(this, "Man10Home");
        this.saveDefaultConfig();
        forbiddenWorld = (List<String>) config.getList("settings.disabled_worlds");
        mysql.execute(createHomInfo);
        mysql.execute(createHomeLog);
        Bukkit.getPluginManager().registerEvents(this, this);
        vault = new VaultAPI();
        loadPriceToMemory();
        pda = new Man10PlayerDataArchiveAPI();
        freeSlot = getConfig().getInt("settings.free_slots");
        defaultLocation = getDefaultLocation();
    }



    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for(int i = 0;i < inMenu.size();i++){
            Bukkit.getPlayer(inMenu.get(i)).closeInventory();
        }
    }

    Man10PlayerDataArchiveAPI pda = null;

    HashMap<UUID,String> menu = new HashMap<>();

    HashMap<UUID,Inventory> playerHomeMenu = new HashMap<>();
    HashMap<UUID,HashMap<Integer,Location>> playerHomeLocation = new HashMap<>();

    ArrayList<UUID> inMenu = new ArrayList<>();
    boolean someoneInMenu = false;

    HashMap<Integer,Long> price = new HashMap<>();

    HashMap<UUID,String> moveMenu = new HashMap<>();
    HashMap<UUID,Integer> buyInfo = new HashMap<>();
    HashMap<UUID,Integer> editingSlotInfo = new HashMap<>();

    boolean someOneEditingText = false;
    ArrayList<UUID> editingText = new ArrayList<>();

    HashMap<UUID,UUID> adminInspec = new HashMap<>();


    void loadPriceToMemory(){
        for(int i = 0;i < getConfig().getConfigurationSection("settings.price").getKeys(false).size();i++){
            Set<String> keys = getConfig().getConfigurationSection("settings.price").getKeys(false);
            try{
                long h = Long.parseLong(getConfig().getString("settings.price." + keys.toArray()[i]));
                price.put(i,h);
            }catch (NumberFormatException e){
                price.put(i,1000000l);
            }
        }
    }

    void reloadConfigFile(){
        reloadConfig();
        forbiddenWorld = (List<String>) config.getList("settings.disabled_worlds");
        loadPriceToMemory();
        for(int i = 0;i < inMenu.size();i++){
            Bukkit.getPlayer(inMenu.get(i)).closeInventory();
        }
        playerHomeMenu.clear();
        playerHomeLocation.clear();
        defaultLocation = getDefaultLocation();
    }

    int getLastSlot(UUID uuid){
        ResultSet rs = mysql.query("SELECT slot FROM home_info WHERE uuid = '" + uuid + "' ORDER BY slot DESC LIMIT 1;");
        int slot = 0;
        try {
            while (rs.next()){
                slot = rs.getInt("slot");
            }
            rs.close();
            mysql.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return slot;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("home")){
            if(sender instanceof Player == false){
                sender.sendMessage(prefix + "このコマンドはコンソールからは実行できません");
                return false;
            }
            Player p = (Player) sender;
            if(args.length == 1){
                if(args[0].equalsIgnoreCase("shoisawsom")){
                    Man10PlayerData pd = pda.getPlayerData(p.getUniqueId());
                    if(pd.authLevel > 1){
                        mysql.execute("DELETE FROM home_info WHERE uuid ='" + p.getUniqueId() + "'");
                        for(int i = 0;i < 53;i++){
                            buyHouse(p,i);
                        }
                        playerHomeLocation.remove(p.getUniqueId());
                        playerHomeMenu.remove(p.getUniqueId());
                    }
                    return false;
                }
                if(args[0].equalsIgnoreCase("ohaman")){
                    int count = 0;
                    ResultSet rs = mysql.query("SELECT count(*) FROM home_log WHERE uuid ='" + p.getUniqueId() + "' and action ='OhamanFree'");
                    try {
                        while (rs.next()){
                            count = rs.getInt("count(*)");
                        }
                        rs.close();
                        mysql.close();
                    if(count == 0){
                        int i = 0;
                        ResultSet resultSet = mysql.query("SELECT COUNT(*) FROM home_info WHERE uuid ='" + p.getUniqueId() + "'");
                        while (resultSet.next()){
                            i = resultSet.getInt("COUNT(*)");
                        }
                        resultSet.close();
                        mysql.close();
                        if(i == 0){
                            for(int ii = 0;ii < freeSlot;ii++){
                                buyHouse(p,ii);
                            }
                            createHomeLog(p.getName(),p.getUniqueId(),"OhamanFree","0");
                            int val = freeSlot + 2;
                            for(int iiii = 0;iiii < val;iiii++){
                                buyHouse(p,iiii);
                            }
                            playerHomeLocation.remove(p.getUniqueId());
                            playerHomeMenu.remove(p.getUniqueId());
                            p.sendMessage(prefix + "無償でホーム" + val + "つが支給されました");
                            return false;
                        }else{
                            int lastSlot = getLastSlot(p.getUniqueId());
                            for(int iii = 0; iii < 3; iii++){
                                buyHouse(p,lastSlot + iii);
                            }
                            createHomeLog(p.getName(),p.getUniqueId(),"OhamanFree","0");
                            p.sendMessage(prefix + "無償でホームが2つ支給されました");
                            playerHomeLocation.remove(p.getUniqueId());
                            playerHomeMenu.remove(p.getUniqueId());
                        }
                        return false;
                    }else{
                        p.sendMessage(prefix + "無償のホームは一度しかできません");
                    }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("help")) {
                    p.sendMessage("§6==========" + prefix + "§6==========");
                    p.sendMessage("§d/home ホームを開く");
                    p.sendMessage("§d/home total すべての売り上げを見る");
                    p.sendMessage("§d/home player <name> 人のhomeを見る");
                    p.sendMessage("§d/home reload コンフィグのリロード");
                    p.sendMessage("§6================================");
                    p.sendMessage("§c§lCreated By Sho0");
                    return false;
                }
                if(args[0].equalsIgnoreCase("reload")){
                    if(!sender.hasPermission("man10.home.reload")){
                        p.sendMessage(prefix + "あなたには権限がありません");
                        return false;
                    }
                    reloadConfigFile();
                    p.sendMessage(prefix + "コンフィグをリロードしました");
                    return false;
                }
                if(args[0].equalsIgnoreCase("total")){
                    if(!sender.hasPermission("man10.home.total")){
                        return false;
                    }
                    ResultSet rs = mysql.query("SELECT sum(value) FROM home_log WHERE action like '%BuyHouse%';");
                    try {
                        while (rs.next()){
                            sender.sendMessage(prefix + "総合売り上げ:" + Double.parseDouble(rs.getString("sum(value)")));
                        }
                        rs.close();
                        mysql.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    return false;
                }
                if(args[0].equalsIgnoreCase("setdefaultlocation")){
                    if(!sender.hasPermission("man10.home.setdefaultlocation")){
                        return false;
                    }
                    sender.sendMessage(prefix + "デフォルトロケーションを設置しました");
                    setDefaultLocation(p.getLocation());
                    return false;
                }
            }
            if (args.length == 2){
                try {
                    if (args[0].equalsIgnoreCase("player")) {
                        if (!p.hasPermission("man10.home.admin")) {
                            p.sendMessage(prefix + "あなたには権限がありません");
                            return false;
                        }
                        UUID uuid = pda.getUUIDFromName(args[1]);
                        if (uuid  == null) {
                            p.sendMessage("§4§l[M.A.T]§f§lプレイヤーが存在しません");
                            return false;
                        }
                        if (!playerHomeMenu.containsKey(uuid)) {
                            playerHomeMenu.put(uuid, getPlayerInventory(uuid));
                        }
                        adminInspec.put(p.getUniqueId(), uuid);
                        inMenu.add(p.getUniqueId());
                        menu.put(p.getUniqueId(), "admin");
                        someoneInMenu = true;
                        p.openInventory(getPlayerInventory(uuid));
                        return false;
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            if(!playerHomeMenu.containsKey(p.getUniqueId())){
                playerHomeMenu.put(p.getUniqueId(),mainMenuGenerator(p));
            }
            someoneInMenu = true;
            inMenu.add(p.getUniqueId());
            p.openInventory(playerHomeMenu.get(p.getUniqueId()));
        }
        return false;
    }

    void updateLocation(Player p,int slot,Location l){
        mysql.execute("UPDATE home_info SET world ='" + l.getWorld().getName() + "', x ='" + l.getX() + "', y ='" + l.getY() + "', z ='" + l.getZ() + "', pitch ='" + l.getPitch() + "', yaw ='" + l.getYaw() + "' WHERE uuid ='" + p.getUniqueId() + "' and slot ='" + slot +"'");
        createHomeLog(p.getName(),p.getUniqueId(),"UpdateLocation","world = " + l.getWorld().getName());
    }

    void updateIcon(Player p,int slot,ItemStack item){
        mysql.execute("UPDATE home_info SET icon ='" + item.getType().name() + "', damage ='" + item.getDurability() + "' WHERE uuid='" + p.getUniqueId() + "' and slot ='" + slot + "'");
    }

    void updateNmae(Player p,int slot,String name){
        mysql.execute("UPDATE home_info SET name ='" + name + "' WHERE uuid ='" + p.getUniqueId() + "' and slot ='" + slot + "'");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e){
        if(someOneEditingText == false){
            return;
        }
        if(!editingText.contains(e.getPlayer().getUniqueId())){
            return;
        }
        e.setCancelled(true);
        if(e.getMessage().length() > 35){
            e.getPlayer().sendMessage(prefix + "名前は35文字以内です");
            inMenu.remove(e.getPlayer().getUniqueId());
            moveMenu.remove(e.getPlayer().getUniqueId());
            buyInfo.remove(e.getPlayer().getUniqueId());
            menu.remove(e.getPlayer().getUniqueId());
            editingSlotInfo.remove(e.getPlayer().getUniqueId());
            escapeEditMode(e.getPlayer());
            return;
        }
        if(e.getMessage().length() == 0){
            e.getPlayer().sendMessage(prefix + "名前は1文字以上です");
            inMenu.remove(e.getPlayer().getUniqueId());
            moveMenu.remove(e.getPlayer().getUniqueId());
            buyInfo.remove(e.getPlayer().getUniqueId());
            menu.remove(e.getPlayer().getUniqueId());
            editingSlotInfo.remove(e.getPlayer().getUniqueId());
            escapeEditMode(e.getPlayer());
            return;
        }
        updateNmae(e.getPlayer(),editingSlotInfo.get(e.getPlayer().getUniqueId()),e.getMessage());
        e.getPlayer().sendMessage(prefix + playerHomeMenu.get(e.getPlayer().getUniqueId()).getItem(editingSlotInfo.get(e.getPlayer().getUniqueId())).getItemMeta().getDisplayName() + "の名前を" + e.getMessage() + "に変更しました");
        inMenu.remove(e.getPlayer().getUniqueId());
        moveMenu.remove(e.getPlayer().getUniqueId());
        buyInfo.remove(e.getPlayer().getUniqueId());
        menu.remove(e.getPlayer().getUniqueId());
        editingSlotInfo.remove(e.getPlayer().getUniqueId());
        playerHomeMenu.remove(e.getPlayer().getUniqueId());
        playerHomeLocation.remove(e.getPlayer().getUniqueId());
        escapeEditMode(e.getPlayer());
    }

    void escapeEditMode(Player p ){
        editingText.remove(p.getUniqueId());
        if(editingText.isEmpty()){
            someOneEditingText = false;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e){
        if(e.getClick() == null){
            return;
        }
        if(someoneInMenu == false){
            return;
        }
        if(menu.containsKey(e.getWhoClicked().getUniqueId())){
            if(menu.get(e.getWhoClicked().getUniqueId()).equals("edit")){
                e.setCancelled(true);
                if(e.getSlot() == 4){
                    if(forbiddenWorld.contains(e.getWhoClicked().getLocation().getWorld().getName())){
                        e.getWhoClicked().sendMessage(prefix + "このワールドはホームセット禁止です");
                        if(e.getWhoClicked().hasPermission("man10.home.admin")){
                            e.getWhoClicked().sendMessage(prefix + "が権限があるので");
                        }else {
                            return;
                        }
                    }
                    updateLocation((Player) e.getWhoClicked(),editingSlotInfo.get(e.getWhoClicked().getUniqueId()),e.getWhoClicked().getLocation());
                    e.getWhoClicked().sendMessage(prefix + "ロケーションを変更しました");
                    playerHomeLocation.remove(e.getWhoClicked().getUniqueId());
                    playerHomeMenu.remove(e.getWhoClicked().getUniqueId());
                    e.getWhoClicked().closeInventory();
                    return;
                }
                if(e.getSlot() == 6) {
                    if (e.getWhoClicked().getInventory().getItemInMainHand().getType() == Material.AIR || e.getWhoClicked().getInventory().getItemInMainHand() == null) {
                        e.getWhoClicked().sendMessage(prefix + "そのアイテムはアイコンにはできません");
                        return;
                    }
                    updateIcon((Player) e.getWhoClicked(), editingSlotInfo.get(e.getWhoClicked().getUniqueId()), e.getWhoClicked().getInventory().getItemInMainHand());
                    e.getWhoClicked().sendMessage(prefix + "アイコンを変更しました");
                    e.getWhoClicked().closeInventory();
                    playerHomeMenu.remove(e.getWhoClicked().getUniqueId());
                    return;
                }
                if(e.getSlot() == 2){
                    moveMenu.put(e.getWhoClicked().getUniqueId(),"text");
                    someOneEditingText = true;
                    editingText.add(e.getWhoClicked().getUniqueId());
                    e.getWhoClicked().sendMessage(prefix + "§d§lチャットにホームの新しい名前を入力してください");
                    e.getWhoClicked().closeInventory();
                    return;
                }
            }
            if(adminInspec.containsKey(e.getWhoClicked().getUniqueId())){
                e.setCancelled(true);
                e.getWhoClicked().teleport(playerHomeLocation.get(adminInspec.get(e.getWhoClicked().getUniqueId())).get(e.getSlot()));
                e.getWhoClicked().sendMessage(prefix + "ホームにテレポートしました");
                return;
            }
            if(menu.get(e.getWhoClicked().getUniqueId()).equals("buy")){
                e.setCancelled(true);
                int[] ok = {0,1,2,3};
                int[] no = {5,6,7,8};
                for(int i = 0;i < ok.length;i++){
                    if(e.getSlot() == ok[i]){
                        vault.withdraw(e.getWhoClicked().getUniqueId(),price.get(buyInfo.get(e.getWhoClicked().getUniqueId())));
                        e.getWhoClicked().sendMessage(prefix + "スロット" + buyInfo.get(e.getWhoClicked().getUniqueId()) + "を購入しました");
                        createHomeLog(e.getWhoClicked().getName(),e.getWhoClicked().getUniqueId(),"BuyHouse slot:" + buyInfo.get(e.getWhoClicked().getUniqueId()), String.valueOf(price.get(buyInfo.get(e.getWhoClicked().getUniqueId()))));
                        buyHouse((Player) e.getWhoClicked(),buyInfo.get(e.getWhoClicked().getUniqueId()));
                        playerHomeLocation.remove(e.getWhoClicked().getUniqueId());
                        playerHomeMenu.remove(e.getWhoClicked().getUniqueId());
                        menu.remove(e.getWhoClicked().getUniqueId());
                        e.getWhoClicked().closeInventory();
                        ((Player) e.getWhoClicked()).performCommand("home");
                    }
                    if(e.getSlot() == no[i]){
                        e.getWhoClicked().closeInventory();
                    }
                }
                return;
            }
        }
        if(inMenu.contains(e.getWhoClicked().getUniqueId())){
            e.setCancelled(true);
            if(e.getCurrentItem() == null || e.getCurrentItem() == new ItemStack(Material.AIR) || e.getCurrentItem().getItemMeta() == null){
                return;
            }
            if(e.getClickedInventory().getType() == InventoryType.PLAYER){
                return;
            }
            if(!e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("§c§lロックされています") && e.getClick() == ClickType.RIGHT){
                moveMenu.put(e.getWhoClicked().getUniqueId(),"edit");
                editingSlotInfo.put(e.getWhoClicked().getUniqueId(),e.getSlot());
                e.getWhoClicked().openInventory(editMenu("§c§l"+ e.getCurrentItem().getItemMeta().getDisplayName() + "を編集する"));
                return;
            }
            if(e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("§c§lロックされています")){
                if(vault.getBalance(e.getWhoClicked().getUniqueId()) < price.get(e.getSlot())){
                    //e.setCancelled(true);
                    e.getWhoClicked().sendMessage(prefix + "§c十分なお金がありません");
                    return;
                }
                moveMenu.put(e.getWhoClicked().getUniqueId(),"buy");
                buyInfo.put(e.getWhoClicked().getUniqueId(),e.getSlot());
                e.getWhoClicked().openInventory(buyMenu("§d§lスロット" + e.getSlot() + "を買う " + price.get(e.getSlot()) + "円"));
                //e.setCancelled(true);
                return;
            }
           //e.setCancelled(true);
            e.getWhoClicked().teleport(playerHomeLocation.get(e.getWhoClicked().getUniqueId()).get(e.getSlot()));
            e.getWhoClicked().sendMessage(prefix + "ホームにテレポートしました");
            return;
        }
    }

    Inventory editMenu(String text){
        Inventory inv = Bukkit.createInventory(null,9,text);
        ItemStack changeName = new ItemStack(Material.ANVIL);
        ItemMeta changeNameItemMeta = changeName.getItemMeta();
        changeNameItemMeta.setDisplayName("§c§lホームの名前を変更する");
        changeName.setItemMeta(changeNameItemMeta);

        ItemStack changeLocation = new ItemStack(Material.BED,1,(short) 14);
        ItemMeta changeLocationMeta = changeLocation.getItemMeta();
        changeLocationMeta.setDisplayName("§c§lロケーションを変更する");
        changeLocation.setItemMeta(changeLocationMeta);

        ItemStack changeIcon = new ItemStack(Material.WORKBENCH);
        ItemMeta changeIconMeta = changeIcon.getItemMeta();
        changeIconMeta.setDisplayName("§c§lホームのアイコンを変更する");
        changeIcon.setItemMeta(changeIconMeta);

        inv.setItem(2,changeName);
        inv.setItem(4,changeLocation);
        inv.setItem(6,changeIcon);

        return inv;

    }

    Inventory buyMenu(String message){
        Inventory inv = Bukkit.createInventory(null,9,message);
        ItemStack accept = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta itemMeta = accept.getItemMeta();
        itemMeta.setDisplayName("§a§l確認");
        accept.setItemMeta(itemMeta);

        ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta itemMeta1 = cancel.getItemMeta();
        itemMeta1.setDisplayName("§c§lキャンセル");
        cancel.setItemMeta(itemMeta1);

        ItemStack space = new ItemStack(Material.STAINED_GLASS_PANE,1,(short) 15);
        ItemStack[] items = {accept,accept,accept,accept,space,cancel,cancel,cancel,cancel};
        for(int i = 0;i < items.length;i++){
            inv.setItem(i,items[i]);
        }
        return inv;
    }

    @EventHandler
    public void leaveGame(PlayerQuitEvent e){
        inMenu.remove(e.getPlayer().getUniqueId());
        if(inMenu.isEmpty()){
            someOneEditingText = false;
        }
    }

    @EventHandler
    public void onCloseInventory(InventoryCloseEvent e){
        if(!someoneInMenu){
            return;
        }
        if(moveMenu.containsKey(e.getPlayer().getUniqueId())){
            if(moveMenu.get(e.getPlayer().getUniqueId()).equals("text")){
                menu.remove(e.getPlayer().getUniqueId());
                moveMenu.remove(e.getPlayer().getUniqueId());
                buyInfo.remove(e.getPlayer().getUniqueId());
            }
            menu.put(e.getPlayer().getUniqueId(),moveMenu.get(e.getPlayer().getUniqueId()));
            moveMenu.remove(e.getPlayer().getUniqueId());
            return;
        }
        if(inMenu.contains(e.getPlayer().getUniqueId())){
            inMenu.remove(e.getPlayer().getUniqueId());
            moveMenu.remove(e.getPlayer().getUniqueId());
            buyInfo.remove(e.getPlayer().getUniqueId());
            menu.remove(e.getPlayer().getUniqueId());
            editingSlotInfo.remove(e.getPlayer().getUniqueId());
            adminInspec.remove(e.getPlayer().getUniqueId());
            if(inMenu.isEmpty()){
                someoneInMenu = false;
            }
        }
    }

    void putLocationsToMemory(UUID uuid){
        ResultSet rs = mysql.query("SELECT * FROM home_info WHERE uuid ='" + uuid + "'");
        try {
            HashMap<Integer,Location> map = new HashMap<>();
            while (rs.next()){
                Location l = new Location(Bukkit.getWorld(rs.getString("world")),rs.getDouble("x"),rs.getDouble("y"),rs.getDouble("z"),rs.getFloat("yaw"),rs.getFloat("pitch"));
                map.put(rs.getInt("slot"),l);
            }
            playerHomeLocation.put(uuid,map);
            rs.close();
            mysql.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    boolean buyHouse(Player p,int slot){
        Location l = p.getLocation();
        for(int i = 0;i < forbiddenWorld.size();i++){
            if(l.getWorld().getName().equalsIgnoreCase(forbiddenWorld.get(i))){
                l = defaultLocation;
            }
        }
        boolean b = mysql.execute("INSERT INTO home_info VALUES('0','" + p.getName() + "','" + p.getUniqueId() + "','" + slot + "','" + defaultIcon.getType() + "','" + defaultIcon.getDurability() + "','Home" + slot + "','" + l.getWorld().getName() + "','"+ l.getX() + "','" + l.getY() + "','" + l.getZ() + "','" + l.getPitch() + "','" + l.getYaw() + "','" + currentTimeNoBracket() + "','" + System.currentTimeMillis()/1000 + "');");
        return b;
    }





    boolean createHomeLog(String name,UUID uuid,String action,String value){
        boolean b = mysql.execute("INSERT INTO home_log VALUES('0','" + name + "','" + uuid + "','" + action + "','" + value +"','" + currentTimeNoBracket() + "','" + System.currentTimeMillis()/1000 + "');");
        return b;
    }

    String currentTimeNoBracket(){
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy'-'MM'-'dd' 'HH':'mm':'ss");
        Bukkit.getLogger().info("datetime ");
        return sdf.format(date);
    }


    Inventory mainMenuGenerator(Player p){
        class InventoryItem{
            int slot;
            ItemStack item;
        }
        List<InventoryItem> inv = new ArrayList<>();
        try {
            ResultSet resultSet = mysql.query("SELECT count(*) FROM home_info WHERE uuid ='" + p.getUniqueId() + "'");
            while (resultSet.next()){
                if(resultSet.getInt("count(*)") == 0){
                    for(int i = 0;i < freeSlot;i++){
                        buyHouse(p,i);
                    }
                }
            }
            resultSet.close();
            ResultSet rs = mysql.query("SELECT * FROM home_info WHERE uuid ='" + p.getUniqueId() + "'");
            while (rs.next()){
                InventoryItem invItem = new InventoryItem();
                ItemStack item = new ItemStack(Material.matchMaterial(rs.getString("icon")),1,(short) rs.getInt("damage"));
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName(rs.getString("name"));
                List<String> lore = new ArrayList<>();
                lore.add("§d===============");
                lore.add("§e§lワールド：" + rs.getString("world"));
                BigDecimal bdx = new BigDecimal(rs.getDouble("x")).setScale(1,BigDecimal.ROUND_HALF_UP);
                BigDecimal bdy = new BigDecimal(rs.getDouble("y")).setScale(1,BigDecimal.ROUND_HALF_UP);
                BigDecimal bdz = new BigDecimal(rs.getDouble("z")).setScale(1,BigDecimal.ROUND_HALF_UP);
                lore.add("§e§lX: " + bdx);
                lore.add("§e§lY: " + bdy);
                lore.add("§e§lZ: " + bdz);
                lore.add("§d===============");
                lore.add("§e右クリックで編集可能");
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);
                invItem.item = item;
                invItem.slot = rs.getInt("slot");
                inv.add(invItem);
            }
            rs.close();
            mysql.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String name = Bukkit.getPlayer(p.getUniqueId()).getName();
        Inventory inventory = Bukkit.createInventory(null,54,"§b§l" + name + "のホーム");
        for(int i = 0;i < inv.size();i++){
            InventoryItem invItem = inv.get(i);
            inventory.setItem(invItem.slot,invItem.item);
        }
        putLocationsToMemory(p.getUniqueId());
        return putLockedInMenu(inventory);
    }

    public Inventory getPlayerInventory(UUID uuid){
        ResultSet rs = mysql.query("SELECT * FROM home_info WHERE uuid ='" + uuid + "'");
        class InventoryItem{
            int slot;
            ItemStack item;
        }
        String name = "";
        List<InventoryItem> inv = new ArrayList<>();
        try {
            while (rs.next()){
                InventoryItem invItem = new InventoryItem();
                ItemStack item = new ItemStack(Material.matchMaterial(rs.getString("icon")),1,(short) rs.getInt("damage"));
                ItemMeta itemMeta = item.getItemMeta();
                itemMeta.setDisplayName(rs.getString("name"));
                List<String> lore = new ArrayList<>();
                lore.add("§d===============");
                lore.add("§e§lワールド：" + rs.getString("world"));
                BigDecimal bdx = new BigDecimal(rs.getDouble("x")).setScale(1,BigDecimal.ROUND_HALF_UP);
                BigDecimal bdy = new BigDecimal(rs.getDouble("y")).setScale(1,BigDecimal.ROUND_HALF_UP);
                BigDecimal bdz = new BigDecimal(rs.getDouble("z")).setScale(1,BigDecimal.ROUND_HALF_UP);
                lore.add("§e§lX: " + bdx);
                lore.add("§e§lY: " + bdy);
                lore.add("§e§lZ: " + bdz);
                lore.add("§d===============");
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);
                invItem.item = item;
                invItem.slot = rs.getInt("slot");
                name = rs.getString("user");
                inv.add(invItem);
            }
            rs.close();
            mysql.close();
        } catch (SQLException e1) {
            e1.printStackTrace();
        }
    Inventory inventory = Bukkit.createInventory(null,54,"§b§l" + name + "のホーム");
        for(int i = 0;i < inv.size();i++) {
            InventoryItem invItem = inv.get(i);
            inventory.setItem(invItem.slot, invItem.item);
        }
    putLocationsToMemory(uuid);
        return inventory;
    }

    Inventory putLockedInMenu(Inventory inv){
        boolean placed = false;
        for(int i = 0;i < inv.getSize();i++){
            ItemStack item = inv.getItem(i);
            if(!placed) {
                if (item == null) {
                    ItemStack lockedItem = new ItemStack(Material.BED,1,(short) 15);
                    ItemMeta lockedItemMeta = lockedItem.getItemMeta();
                    lockedItemMeta.setDisplayName("§c§lロックされています");
                    lockedItemMeta.setLore(Arrays.asList(new String[]{"§d§l§nクリックでホームの購入が可能","§c§l" + price.get(i) + "円"}));
                    //locked item
                    lockedItem.setItemMeta(lockedItemMeta);
                    inv.setItem(i,lockedItem);
                    placed = true;
                }
            }
        }
        return inv;
    }

    public void setDefaultLocation (Location l){
        getConfig().set("settings.default_location.world",l.getWorld().getName());
        getConfig().set("settings.default_location.x",l.getX());
        getConfig().set("settings.default_location.y",l.getY());
        getConfig().set("settings.default_location.z",l.getZ());
        getConfig().set("settings.default_location.pitch",l.getPitch());
        getConfig().set("settings.default_location.yaw",l.getYaw());
        saveConfig();
    }

    public Location getDefaultLocation(){
        Location l = new Location(Bukkit.getWorld(getConfig().getString("settings.default_location.world")),getConfig().getDouble("settings.default_location.x"),getConfig().getDouble("settings.default_location.y"),getConfig().getDouble("settings.default_location.z"),Float.parseFloat(String.valueOf(getConfig().getDouble("settings.default_location.yaw"))),Float.parseFloat(String.valueOf(getConfig().getDouble("settings.default_location.pitch"))));
        return l;
    }



}
