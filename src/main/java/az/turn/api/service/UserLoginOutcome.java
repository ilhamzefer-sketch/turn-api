package az.turn.api;

public record UserLoginOutcome(
        UserEntity user,
        UserLoginFailure failure
) {
    public static UserLoginOutcome success(UserEntity user) {
        return new UserLoginOutcome(user, UserLoginFailure.NONE);
    }

    public static UserLoginOutcome failure(UserEntity user, UserLoginFailure failure) {
        return new UserLoginOutcome(user, failure);
    }
}
