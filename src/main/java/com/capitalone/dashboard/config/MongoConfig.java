package com.capitalone.dashboard.config;

import com.capitalone.dashboard.model.converter.ActiveWidgetReaderConverter;
import com.capitalone.dashboard.repository.RepositoryPackage;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.CustomConversions;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@EnableMongoRepositories(basePackageClasses = RepositoryPackage.class)
public class MongoConfig extends AbstractMongoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${dbname:dashboard}")
    private String databaseName;
    @Value("${dbhost:localhost}")
    private String host;
    @Value("${dbport:27017}")
    private int port;
    @Value("${dbreplicaset:false}")
    private String dbreplicaset;
    @Value("#{'${dbhostport:localhost:27017}'.split(',')}")
    private List<String> hostport;
    @Value("${dbusername:}")
    private String userName;
    @Value("${dbpassword:}")
    private String password;


    @Override
    protected String getDatabaseName() {
        return databaseName;
    }

    @Override
    @Bean
    public MongoClient mongo() throws Exception {

        MongoClient client;
        LOGGER.info("ReplicaSet" + dbreplicaset);

        MongoClientOptions.Builder builder = new MongoClientOptions.Builder();
        builder.maxConnectionIdleTime(60000);
        MongoClientOptions opts = builder.build();

        if (Boolean.parseBoolean(dbreplicaset)) {
            List<ServerAddress> serverAddressList = new ArrayList<>();
            for (String h : hostport) {
                String myHost = h.substring(0, h.indexOf(":"));
                int myPort = Integer.parseInt(h.substring(h.indexOf(":") + 1, h.length()));
                ServerAddress serverAddress = new ServerAddress(myHost, myPort);
                serverAddressList.add(serverAddress);
            }

            for (ServerAddress s : serverAddressList) {
                LOGGER.info("Initializing Mongo Client server ReplicaSet at: {}", s);
            }

            if (StringUtils.isEmpty(userName)) {
                client = new MongoClient(serverAddressList);
            } else {
                MongoCredential mongoCredential = MongoCredential.createScramSha1Credential(
                        userName, databaseName, password.toCharArray());
                client = new MongoClient(serverAddressList, Collections.singletonList(mongoCredential), opts);
            }
        } else {
            ServerAddress serverAddr = new ServerAddress(host, port);
            LOGGER.info("Initializing Mongo Client server at: {}", serverAddr);
            if (StringUtils.isEmpty(userName)) {
                client = new MongoClient(serverAddr);
            } else {
                MongoCredential mongoCredential = MongoCredential.createScramSha1Credential(
                        userName, databaseName, password.toCharArray());
                client = new MongoClient(serverAddr, Collections.singletonList(mongoCredential), opts);
            }

        }
        LOGGER.info("Connecting to Mongo: {}", client);
        return client;
    }

    @Override
    protected String getMappingBasePackage() {
        return com.capitalone.dashboard.model.Application.class.getPackage().getName();
    }

    @Bean
    public MongoTemplate mongoTemplate() throws Exception {

        MongoDbFactory factory = new SimpleMongoDbFactory(mongo(), getDatabaseName());
        return new MongoTemplate(factory , mongoConverter(factory));
    }

    @Override
    public CustomConversions customConversions(){
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new ActiveWidgetReaderConverter());
        return new CustomConversions(converters);
    }

    public MappingMongoConverter mongoConverter(MongoDbFactory factory) throws Exception {
        MongoMappingContext mappingContext = new MongoMappingContext();
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter mongoConverter = new MappingMongoConverter(dbRefResolver, mappingContext);
        mongoConverter.setCustomConversions(customConversions());
        mongoConverter.afterPropertiesSet();
        return mongoConverter;
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
