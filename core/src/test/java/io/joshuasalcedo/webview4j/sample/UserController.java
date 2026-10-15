package io.joshuasalcedo.webview4j.sample;

import io.joshuasalcedo.webview4j.HttpException;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.annotation.Body;
import io.joshuasalcedo.webview4j.annotation.Delete;
import io.joshuasalcedo.webview4j.annotation.Get;
import io.joshuasalcedo.webview4j.annotation.Path;
import io.joshuasalcedo.webview4j.annotation.Post;
import io.joshuasalcedo.webview4j.annotation.Put;
import io.joshuasalcedo.webview4j.annotation.Query;
import io.joshuasalcedo.webview4j.annotation.WebViewController;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** A controller as an app would write one; compiled with the webview-4j annotation processor. */
@WebViewController("/users")
public class UserController {

    public enum Role { ADMIN, MEMBER }

    public record Address(String city, List<String> lines) {}

    public record User(long id, String name, Role role, List<String> tags, Optional<Address> address) {}

    public record CreateUser(String name, Role role, List<String> tags, Optional<Address> address) {}

    private final Map<Long, User> users = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Get("/{id}")
    public User find(@Path long id) {
        User user = users.get(id);
        if (user == null) throw new HttpException(404, "no user " + id);
        return user;
    }

    @Get
    public List<User> search(@Query String name, @Query Role role, @Query("max") Integer limit) {
        return users.values().stream()
                .filter(u -> name == null || u.name().contains(name))
                .filter(u -> role == null || u.role() == role)
                .sorted(Comparator.comparingLong(User::id))
                .limit(limit == null ? Long.MAX_VALUE : limit)
                .toList();
    }

    @Get("/me")
    public String me(View view) {
        return "the page in " + view.id();
    }

    @Post
    public CompletableFuture<User> create(@Body CreateUser body) {
        if (body.name() == null || body.name().isBlank()) throw new IllegalArgumentException("a user needs a name");
        User user = new User(ids.incrementAndGet(), body.name(), body.role(), body.tags(), body.address());
        users.put(user.id(), user);
        return CompletableFuture.supplyAsync(() -> user);
    }

    @Put("/{id}/name")
    public User rename(@Path long id, @Body String name) {
        User user = find(id);
        User renamed = new User(user.id(), name, user.role(), user.tags(), user.address());
        users.put(id, renamed);
        return renamed;
    }

    @Delete("/{id}")
    public void delete(@Path long id) {
        if (users.remove(id) == null) throw new HttpException(404, "no user " + id);
    }
}
