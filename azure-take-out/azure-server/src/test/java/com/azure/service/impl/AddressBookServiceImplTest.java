package com.azure.service.impl;

import com.azure.constant.MessageConstant;
import com.azure.context.BaseContext;
import com.azure.entity.AddressBook;
import com.azure.exception.AddressBookBusinessException;
import com.azure.exception.UserNotLoginException;
import com.azure.mapper.AddressBookMapper;
import com.azure.support.ServiceTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddressBookServiceImplTest extends ServiceTestSupport {
    @InjectMocks private AddressBookServiceImpl service;
    @Mock private AddressBookMapper mapper;

    private AddressBook ownAddress() {
        return AddressBook.builder().id(7L).userId(USER_ID).detail("原地址").build();
    }
    private void invoke(String action, AddressBook address) {
        switch(action) {
            case "list": service.list(address); break;
            case "save": service.save(address); break;
            case "detail": service.getById(address.getId()); break;
            case "update": service.update(address); break;
            case "default": service.setDefault(address); break;
            case "delete": service.deleteById(address.getId()); break;
            default: throw new IllegalArgumentException(action);
        }
    }
    @Test void newAddressUsesAuthenticatedUserAndIsNotDefault() {
        AddressBook address = AddressBook.builder().userId(999L).isDefault(1).phone("13800000000").build();
        service.save(address);
        verify(mapper).insert(address);
        assertThat(address.getUserId()).isEqualTo(USER_ID);
        assertThat(address.getIsDefault()).isZero();
        assertThat(address.getPhone()).isEqualTo("13800000000");
    }
    @Test void listOverwritesForgedUserIdWhilePreservingOtherFilters() {
        AddressBook filter = AddressBook.builder().userId(999L).isDefault(1).phone("13800000000").build();
        when(mapper.list(any())).thenReturn(Collections.singletonList(ownAddress()));
        assertThat(service.list(filter)).containsExactly(ownAddress());
        verify(mapper).list(argThat(a -> a.getUserId().equals(USER_ID) && a.getIsDefault()==1 && a.getPhone().equals("13800000000")));
    }
    @Test void absentListFilterStillRestrictsToAuthenticatedUser() {
        when(mapper.list(any())).thenReturn(Collections.emptyList());
        assertThat(service.list(null)).isEmpty();
        verify(mapper).list(argThat(a -> a.getUserId().equals(USER_ID)));
    }
    @Test void detailReturnsOwnAddress() {
        AddressBook address = ownAddress(); when(mapper.getByIdAndUserId(7L,USER_ID)).thenReturn(address);
        assertThat(service.getById(7L)).isSameAs(address);
        verify(mapper,never()).getById(anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"detail","update","default","delete"})
    void missingAddressRejectsWithoutAnyWrite(String action) {
        assertThatThrownBy(() -> invoke(action,ownAddress())).isInstanceOf(AddressBookBusinessException.class)
                .hasMessage(MessageConstant.ADDRESS_BOOK_NOT_FOUND);
        verify(mapper,never()).update(any()); verify(mapper,never()).updateIsDefaultByUserId(any());
        verify(mapper,never()).deleteById(anyLong(),anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"list","save","detail","update","default","delete"})
    void absentLoginRejectsBeforeDatabaseAccess(String action) {
        BaseContext.removeCurrentId();
        assertThatThrownBy(() -> invoke(action,ownAddress())).isInstanceOf(UserNotLoginException.class);
        verifyNoInteractions(mapper);
    }
    @Test void updateOverwritesForgedUserIdAndKeepsAddressFields() {
        when(mapper.getByIdAndUserId(7L,USER_ID)).thenReturn(ownAddress()); when(mapper.update(any())).thenReturn(1);
        AddressBook request = AddressBook.builder().id(7L).userId(999L).detail("新地址").build();
        service.update(request);
        verify(mapper).update(argThat(a -> a.getUserId().equals(USER_ID) && a.getId()==7L && a.getDetail().equals("新地址")));
    }
    @Test void setDefaultChecksOwnershipBeforeResetAndUpdatesOnlyDefaultFlag() {
        when(mapper.getByIdAndUserId(7L,USER_ID)).thenReturn(ownAddress()); when(mapper.update(any())).thenReturn(1);
        service.setDefault(AddressBook.builder().id(7L).userId(999L).detail("不应写入").build());
        InOrder sequence = inOrder(mapper);
        sequence.verify(mapper).getByIdAndUserId(7L,USER_ID);
        sequence.verify(mapper).updateIsDefaultByUserId(argThat(a -> a.getUserId().equals(USER_ID) && a.getIsDefault()==0));
        sequence.verify(mapper).update(argThat(a -> a.getId()==7L && a.getUserId().equals(USER_ID) && a.getIsDefault()==1 && a.getDetail()==null));
    }
    @Test void deleteOwnAddressUsesAuthenticatedOwnerInSql() {
        when(mapper.getByIdAndUserId(7L,USER_ID)).thenReturn(ownAddress()); when(mapper.deleteById(7L,USER_ID)).thenReturn(1);
        service.deleteById(7L); verify(mapper).deleteById(7L,USER_ID);
    }
    @ParameterizedTest @ValueSource(strings={"update","default","delete"})
    void addressRemovedAfterOwnershipCheckDoesNotReportSuccess(String action) {
        when(mapper.getByIdAndUserId(7L,USER_ID)).thenReturn(ownAddress());
        assertThatThrownBy(() -> invoke(action,ownAddress())).isInstanceOf(AddressBookBusinessException.class);
    }
}
