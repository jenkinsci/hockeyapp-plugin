package net.hockeyapp.jenkins;

import hudson.model.Descriptor;
import hudson.model.Saveable;

public abstract class RadioButtonSupportDescriptor<T extends RadioButtonSupport> extends Descriptor<RadioButtonSupport> implements Saveable {

}
