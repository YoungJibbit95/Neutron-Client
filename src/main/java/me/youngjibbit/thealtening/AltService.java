package me.youngjibbit.thealtening;

/**
 * Microsoft Minecraft sessions use the normal Mojang session endpoints.
 * The old reflection adapter used HTTP for third-party tokens; it is deliberately unavailable
 * until a supported secure integration is provided. No global authlib routes are changed.
 */
public class AltService {
    private EnumAltService currentService = EnumAltService.MOJANG;

    public synchronized void switchService(EnumAltService service) throws NoSuchFieldException, IllegalAccessException {
        if (service != EnumAltService.MOJANG && service != EnumAltService.MICROSOFT) {
            throw new IllegalAccessException("A supported secure provider integration is required.");
        }
        currentService = service;
    }

    public synchronized EnumAltService getCurrentService() { return currentService; }

    public enum EnumAltService { MOJANG, THEALTENING, NEWALT, MICROSOFT }
}
