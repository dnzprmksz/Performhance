package com.monitise.performhance.repositories;

import com.monitise.performhance.entity.Review;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends CrudRepository<Review, Integer> {

    List<Review> findByOrganizationId(int organizationId);

    List<Review> findByTeamId(int teamId);

}