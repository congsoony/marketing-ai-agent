package com.jggroup.commentagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CommentAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommentAgentApplication.class, args);
	}

}
