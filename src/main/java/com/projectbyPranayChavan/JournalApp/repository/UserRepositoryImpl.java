package com.projectbyPranayChavan.JournalApp.repository;



import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.projectbyPranayChavan.JournalApp.entities.User;
import org.springframework.stereotype.Repository;

import java.util.List;

public class UserRepositoryImpl {

    @PersistenceContext
    private EntityManager entityManager;
   // This method will be useful for users with email and who provided sentiment details
    public List<User> getUserForSA() {

        // Get CriteriaBuilder to build the query
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // Create a query that will return User objects
        CriteriaQuery<User> query = cb.createQuery(User.class);

        // Specify that we are querying the User entity
        Root<User> root = query.from(User.class);

        // Condition 1: sentimentAnalysis must be true
        Predicate sentimentCondition =
                cb.isTrue(root.get("sentimentAnalysis"));

        // Condition 2: email should not be null
        Predicate emailNotNull =
                cb.isNotNull(root.get("email"));

        // Apply both conditions
        query.select(root)
                .where(
                        cb.and(
                                emailNotNull,
                                sentimentCondition
                        )
                );

        return entityManager
                .createQuery(query)
                .getResultList();
    }
}
