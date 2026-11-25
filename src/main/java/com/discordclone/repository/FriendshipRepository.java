package com.discordclone.repository;

import com.discordclone.model.Friendship;
import com.discordclone.model.FriendshipStatus;
import com.discordclone.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    
    /**
     * Find friendship by sender and receiver
     */
    Optional<Friendship> findBySenderAndReceiver(User sender, User receiver);
    
    /**
     * Find all friendships where the user is either sender or receiver and status is ACCEPTED
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.sender JOIN FETCH f.receiver "
            + "WHERE (f.sender = :user OR f.receiver = :user) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendships(@Param("user") User user);
    
    /**
     * Find all pending friend requests sent by the user
     */
    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<Friendship> findBySenderAndStatus(User sender, FriendshipStatus status);
    
    /**
     * Find all pending friend requests received by the user
     */
    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status);
    
    /**
     * Check if a friendship exists between two users (in either direction)
     */
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE " +
           "((f.sender = :user1 AND f.receiver = :user2) OR " +
           "(f.sender = :user2 AND f.receiver = :user1)) AND " +
           "f.status = 'ACCEPTED'")
    boolean existsFriendship(@Param("user1") User user1, @Param("user2") User user2);
    
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE f.status = 'ACCEPTED' AND "
            + "((f.sender.id = :first AND f.receiver.id = :second) OR (f.sender.id = :second AND f.receiver.id = :first))")
    boolean areFriends(@Param("first") Long first, @Param("second") Long second);

    /**
     * Find all users who are friends with the given user
     */
    @Query("SELECT CASE WHEN f.sender = :user THEN f.receiver ELSE f.sender END " +
           "FROM Friendship f WHERE (f.sender = :user OR f.receiver = :user) " +
           "AND f.status = 'ACCEPTED'")
    List<User> findFriends(@Param("user") User user);
} 