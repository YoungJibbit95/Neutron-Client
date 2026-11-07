package me.youngjibbit.command.impl;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.*;
import me.youngjibbit.exception.CommandException;
import me.youngjibbit.module.impl.render.Diagnostics;
import me.youngjibbit.module.impl.render.DiagnosticsEditor;
import me.youngjibbit.utils.ChatUtil;
import me.youngjibbit.value.impl.SliderValue;
import net.minecraft.client.Minecraft;
import java.util.Locale;

@CommandInfo(name="diagnostics",aliases={"diag"},description="Edit diagnostics HUD positions and history",
        usage=".diag <edit|on|off|reset|clear> | .diag move <performance|rotation|network|flags> <x> <y>")
public final class DiagnosticsCommand extends Command {
    @Override public void execute(String... args)throws CommandException {
        Diagnostics module=(Diagnostics)Pulsar.INSTANCE.getMm().getModule(Diagnostics.class);
        if(module==null)throw new CommandException("Diagnostics module is unavailable.");
        if(args.length==0)throw new CommandException(getUsage());
        String action=args[0].toLowerCase(Locale.ROOT);
        if(args.length==1)switch(action){
            case "on":module.setToggled(true);ChatUtil.addChatMessage("Diagnostics enabled.",true);return;
            case "off":module.setToggled(false);ChatUtil.addChatMessage("Diagnostics disabled.",true);return;
            case "edit":module.setToggled(true);Minecraft.getMinecraft().displayGuiScreen(new DiagnosticsEditor(module));return;
            case "reset":module.resetPositions();ChatUtil.addChatMessage("Diagnostics positions reset.",true);return;
            case "clear":module.clearHistory();ChatUtil.addChatMessage("Diagnostics correction history cleared.",true);return;
            default:break;
        }
        if("move".equals(action)&&args.length==4){
            String widget=args[1].toLowerCase(Locale.ROOT);
            String name="performance".equals(widget)?"Performance":"rotation".equals(widget)?"Rotation":"network".equals(widget)?"Network":"flags".equals(widget)?"Flags":null;
            if(name==null)throw new CommandException("Unknown widget: "+widget);
            double x,y;
            try{x=Double.parseDouble(args[2]);y=Double.parseDouble(args[3]);}
            catch(NumberFormatException e){throw new CommandException("Use finite numeric coordinates.");}
            if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>16384||Math.abs(y)>16384)throw new CommandException("Coordinates must be finite and between -16384 and 16384.");
            SliderValue px=module.getNumberSetting(name+" X"),py=module.getNumberSetting(name+" Y");
            module.beginConfigUpdate();try{px.setValue(x);py.setValue(y);}finally{module.endConfigUpdate();}
            ChatUtil.addChatMessage(name+" moved to "+x+", "+y+" (screen bounds and grid settings apply when dragging).",true);return;
        }
        throw new CommandException(getUsage());
    }
}
