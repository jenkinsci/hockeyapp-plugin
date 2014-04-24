package net.hockeyapp.jenkins;

import hudson.ExtensionPoint;
import hudson.model.Describable;
import org.kohsuke.stapler.export.ExportedBean;


@ExportedBean
public abstract class RadioButtonSupport implements ExtensionPoint, Describable<RadioButtonSupport> {

}
