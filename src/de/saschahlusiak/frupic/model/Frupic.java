package de.saschahlusiak.frupic.model;

import java.io.File;
import java.io.Serializable;
import android.util.Log;

public class Frupic implements Serializable {

	private static final long serialVersionUID = 12345L;

	int id;
	String full_url, thumb_url;
	String date;
	String username;
	String tags[];
	Object tag;
	
	Frupic() {
		this.tags = null;
		this.username = null;
		this.date = null;
		this.full_url = null;
		this.thumb_url = null;
		this.id = 0;
		this.tag = null;
	}
	
	public String getUsername() {
		if (username == null)
			return "";
		return username;
	}
	
	public String getDate() {
		return date;
	}
	
	public int getId() {
		return id;
	}
	
	public String getUrl() {
		return "http://frupic.frubar.net/" + id;
	}
	
	public String getFullUrl() {
		return full_url;
	}
	
	public File getCachedFile(FrupicFactory factory, boolean thumb) {
		return new File(FrupicFactory.getCacheFileName(factory, this, thumb));
	}
	
	public String getFileName(boolean thumb) {
		if (!thumb)
			return new File(full_url).getName();
		return "frupic_" + id + (thumb ? "_thumb" : "");
	}
	
	public String[] getTags() {
		return tags;
	}
	
	/**
	 * Connects all tags to one string of the form "[tag1, tag2, tag3]"
	 */
	public String getTagsString() {
		if (tags == null)
			return null;
		String s = "";
		
		for (String s2: tags) {
			if (s.length() > 0)
				s += ", ";
			s += s2;
		}
		
		return "[" + s + "]";
	}
}
