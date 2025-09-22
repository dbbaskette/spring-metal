package org.cloudfoundry.samples.music.domain;

public class ApplicationInfo {
    private String[] profiles;
    private String[] services;
    private String instance;
    private boolean llmEnabled;

    public ApplicationInfo(String[] profiles, String[] services, String instance) {
        this.profiles = profiles;
        this.services = services;
        this.instance = instance;
        this.llmEnabled = false;
    }

    public ApplicationInfo(String[] profiles, String[] services, String instance, boolean llmEnabled) {
        this.profiles = profiles;
        this.services = services;
        this.instance = instance;
        this.llmEnabled = llmEnabled;
    }

    public String[] getProfiles() {
        return profiles;
    }

    public void setProfiles(String[] profiles) {
        this.profiles = profiles;
    }

    public String[] getServices() {
        return services;
    }

    public void setServices(String[] services) {
        this.services = services;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }
}
