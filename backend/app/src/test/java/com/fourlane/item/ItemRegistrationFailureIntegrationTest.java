package com.fourlane.item;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fourlane.item.application.*;
import com.fourlane.item.domain.*;
import com.fourlane.item.infra.LocalImageStorage;
import com.fourlane.support.ImageFixtures;
import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.*;

class ItemRegistrationFailureIntegrationTest extends ItemTestSupport {
    @MockitoSpyBean LocalImageStorage storage;
    @MockitoSpyBean ItemImageRepository images;
    @MockitoSpyBean ItemCreationTransaction transaction;
    final ItemRegistrationService.Command command = new ItemRegistrationService.Command("실패 검사", null, 1000, 1000);
    List<ImageUpload> uploads() { return List.of(new ImageUpload(ImageFixtures.PNG.length, () -> new ByteArrayInputStream(ImageFixtures.PNG))); }
    void assertEmpty() throws Exception { assertThat(count("item")).isZero(); assertThat(count("item_image")).isZero(); assertThat(fileCount()).isZero(); }
    @Test void cleansPreviouslyStoredFilesWhenLaterFileStorageFails() throws Exception {
        var calls = new AtomicInteger();
        doAnswer(invocation -> { if (calls.incrementAndGet() == 2) throw new IOException("test-only-second-write"); return invocation.callRealMethod(); })
                .when(storage).store(any(InputStream.class));
        assertThatThrownBy(() -> registration.register(seller, command, List.of(uploads().getFirst(), uploads().getFirst())))
                .isInstanceOf(com.fourlane.global.error.ApiException.class);
        assertEmpty();
    }
    @Test void rollsBackItemAndRemovesFilesOnMetadataFailure() throws Exception {
        doThrow(new DataIntegrityViolationException("test-only-metadata-failure")).when(images).save(any(ItemImage.class));
        assertThatThrownBy(() -> registration.register(seller, command, uploads())).isInstanceOf(DataIntegrityViolationException.class);
        assertEmpty();
    }
    @Test void actualMysqlForeignKeyFailureRollsBackAndDeletesFile() throws Exception {
        assertThatThrownBy(() -> registration.register(Long.MAX_VALUE, command, uploads()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_item_seller");
        assertEmpty();
    }
    @Test void removesFilesWhenCommitPreparationRollsBack() throws Exception {
        doAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    // The row has been inserted and image bytes already exist, but COMMIT has not run.
                    assertThat(count("item")).isEqualTo(1);
                    throw new IllegalStateException("test-only-before-commit");
                }
            });
            return invocation.callRealMethod();
        }).when(transaction).create(anyLong(), any(), anyList(), any());
        assertThatThrownBy(() -> registration.register(seller, command, uploads())).isInstanceOf(IllegalStateException.class);
        assertEmpty();
    }
    @Test void preservesRealCommittedRowsAndFilesWhenCallerFailsAfterCommit() throws Exception {
        var failingCaller = mock(ItemCreationTransaction.class);
        doAnswer(invocation -> {
            transaction.create(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            throw new IllegalStateException("test-only-after-real-commit");
        }).when(failingCaller).create(anyLong(), any(), anyList(), any());
        var service = new ItemRegistrationService(storage, failingCaller);
        assertThatThrownBy(() -> service.register(seller, command, uploads())).isInstanceOf(IllegalStateException.class);
        assertThat(count("item")).isEqualTo(1); assertThat(count("item_image")).isEqualTo(1); assertThat(fileCount()).isEqualTo(1);
    }
    @Test void unknownCompletionIsPreservedByPolicyWithoutClaimingNetworkFaultReproduction() {
        var outcome = new ItemCreationTransaction.Outcome();
        assertThat(outcome.mayDelete()).isTrue();
        outcome.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        assertThat(outcome.mayDelete()).isFalse();
        outcome.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(outcome.mayDelete()).isFalse();
        outcome.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(outcome.mayDelete()).isTrue();
    }
}
