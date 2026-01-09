package ru.vkr.blockchain.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LevelDBConfig {

    private final LevelDBProperties properties;

    @Bean
    public DB levelDB() throws IOException {
        Options options = new Options();
        options.createIfMissing(properties.isCreateIfMissing());
        options.cacheSize(properties.getCacheSize());

        File dbDir = new File(properties.getPath());
        if (!dbDir.exists()) {
            dbDir.mkdirs();
            log.info("Created LevelDB directory: {}", dbDir.getAbsolutePath());
        }

        DB db = Iq80DBFactory.factory.open(dbDir, options);
        log.info("LevelDB initialized at: {}", dbDir.getAbsolutePath());

        return db;
    }
}
