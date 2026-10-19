package dev.apisentinel.checks;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

final class LimitedBodyHandler implements HttpResponse.BodyHandler<Void> {
    private static final int MAX_RESPONSE_BYTES = 16 * 1024;

    @Override
    public HttpResponse.BodySubscriber<Void> apply(HttpResponse.ResponseInfo responseInfo) {
        return new Subscriber();
    }

    private static final class Subscriber implements HttpResponse.BodySubscriber<Void>, Flow.Subscriber<List<ByteBuffer>> {
        private final CompletableFuture<Void> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int byteCount;

        @Override
        public CompletionStage<Void> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) byteCount += buffer.remaining();
            if (byteCount > MAX_RESPONSE_BYTES) {
                subscription.cancel();
                body.complete(null);
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable error) { body.completeExceptionally(error); }

        @Override
        public void onComplete() { body.complete(null); }
    }
}