package ratelimit;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.net.Administration.*;
import mindustry.plugin.Plugin;

public class RateLimit extends Plugin{
    private ObjectMap<String, Ratekeeper> idToRate = new ObjectMap<>();
    private IntIntMap placed = new IntIntMap();

    @Override
    public void init(){
        Events.on(BlockBuildEndEvent.class, e -> {
            //players should be able to configure their own tiles
            if(e.player != null){
                placed.put(e.tile.pos(), e.player.id);
            }
        });

        //block interaction rate limit
        Vars.netServer.admins.addActionFilter(action -> {
            if(action.type != ActionType.breakBlock &&
            action.type != ActionType.placeBlock &&
            action.type != ActionType.tapTile &&
            Config.antiSpam.bool() && placed.get(action.tile.pos(), -1) != action.player.id){
                int window = Core.settings.getInt("rateWindow", 6);
                int limit = Core.settings.getInt("rateLimit", 25);
                int kickLimit = Core.settings.getInt("rateKickLimit", 60);

                Ratekeeper rate = idToRate.getOr(action.player.uuid, Ratekeeper::new);
                if(rate.allow(window * 1000, limit)){
                    return true;
                }else{
                    if(rate.occurences > kickLimit){
                        action.player.con.kick("You are interacting with too many blocks.", 1000 * 30);
                    }else{
                        action.player.sendMessage("[scarlet]You are interacting with blocks too quickly.");
                    }

                    return false;
                }
            }
            return true;
        });
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("rateconfig", "<window/limit/kickLimit> <value>", "Set configuration values for the rate limit.", args -> {
            String key = "rate" + Strings.capitalize(args[0]);

            if(!(key.equals("rateWindow") || key.equals("rateLimit") || key.equals("rateKickLimit"))){
                Log.err("Not a valid config value: {0}", args[0]);
                return;
            }

            if(Strings.canParseInt(args[1])){
                Core.settings.putSave(key, Integer.parseInt(args[1]));
                Log.info("Ratelimit config value '{0}' set to '{1}'.", key, args[1]);
            }else{
                Log.err("Not a number: {0}", args[1]);
            }
        });
    }

    /** Keeps track of X actions in Y units of time. */
    public static class Ratekeeper{
        public int occurences;
        public long lastTime;

        /**
         * @return whether an action is allowed.
         * @param spacing the spacing between action chunks in milliseconds
         * @param cap the maximum amount of actions per chunk
         * */
        public boolean allow(long spacing, int cap){
            if(Time.timeSinceMillis(lastTime) > spacing){
                occurences = 0;
                lastTime = Time.millis();
            }

            occurences ++;
            return occurences <= cap;
        }
    }
}
