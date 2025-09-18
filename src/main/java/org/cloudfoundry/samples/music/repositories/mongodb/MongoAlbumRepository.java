package org.cloudfoundry.samples.music.repositories.mongodb;

import org.cloudfoundry.samples.music.domain.Album;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
@Primary
@Profile("mongodb")
public interface MongoAlbumRepository extends MongoRepository<Album, String> {
}
