package edu.columbia.irt.netserv.controller.core.service;

public class ServiceEvent {

    public final String[] moduleAdded;
    public final String[] moduleRemoved;
    public final String[] resultingModuleList;

    public ServiceEvent(
            String[] moduleAdded,
            String[] moduleRemoved,
            String[] resultingModuleList) {
        this.moduleAdded = moduleAdded;
        this.moduleRemoved = moduleRemoved;
        this.resultingModuleList = resultingModuleList;
    }
}
