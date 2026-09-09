package com.nada.kasir.navigation

import androidx.lifecycle.ViewModel
import com.nada.kasir.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Wrapper tipis supaya SessionManager (singleton biasa) bisa diambil lewat
 * hiltViewModel() dari Composable manapun di NavGraph, memakai instance
 * SessionManager yang sama (karena SessionManager sendiri @Singleton).
 */
@HiltViewModel
class SessionHolderViewModel @Inject constructor(
    val sessionManager: SessionManager
) : ViewModel()
