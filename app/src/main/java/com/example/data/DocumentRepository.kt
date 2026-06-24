package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val documentDao: DocumentDao) {
    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()

    fun searchDocuments(query: String): Flow<List<Document>> {
        return documentDao.searchDocuments("%$query%")
    }

    suspend fun getDocumentById(id: Int): Document? {
        return documentDao.getDocumentById(id)
    }

    suspend fun insert(document: Document): Long {
        return documentDao.insertDocument(document)
    }

    suspend fun update(document: Document) {
        documentDao.updateDocument(document)
    }

    suspend fun delete(document: Document) {
        documentDao.deleteDocument(document)
    }
    
    suspend fun deleteById(id: Int) {
        documentDao.deleteDocumentById(id)
    }
}
