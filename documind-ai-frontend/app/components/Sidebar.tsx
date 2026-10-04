"use client";

import React, { useState, useRef, useEffect } from 'react';
import './Sidebar.css';

interface Document {
  id: string;
  filename: string;
  originalFilename: string;
  fileSize: number;
  status: string;
}

// Inline SVGs replacing lucide-react to avoid NPM corporate proxy block
const ZapIcon = () => <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>;
const UploadCloudIcon = () => <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="16 16 12 12 8 16"></polyline><line x1="12" y1="12" x2="12" y2="21"></line><path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3"></path><polyline points="16 16 12 12 8 16"></polyline></svg>;
const FileTextIcon = () => <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>;
const Trash2Icon = () => <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>;
const CheckCircleIcon = () => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>;
const ClockIcon = () => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>;

import { useSession } from "next-auth/react";

export default function Sidebar() {
  const { data: session } = useSession();
  const [documents, setDocuments] = useState<Document[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (session?.idToken) {
      fetchDocuments();
    }
  }, [session?.idToken]);

  const fetchDocuments = async () => {
    if (!session?.idToken) return;
    try {
      const res = await fetch('http://localhost:8080/api/v1/documents', {
        headers: {
          'Authorization': `Bearer ${session.idToken}`
        }
      });
      if (res.ok) {
        const data = await res.json();
        setDocuments(data);
      }
    } catch (e) {
      console.warn("Backend not running, using mock data");
      setDocuments([
        { id: '1', filename: 'employee_handbook.pdf', originalFilename: 'employee_handbook.pdf', fileSize: 1024000, status: 'PROCESSED' }
      ]);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      await uploadFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      await uploadFile(e.target.files[0]);
    }
  };

  const uploadFile = async (file: File) => {
    if (!session?.idToken) return;
    setIsUploading(true);
    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await fetch('http://localhost:8080/api/v1/documents', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${session.idToken}`
        },
        body: formData
      });
      
      if (res.ok) {
        await fetchDocuments();
      }
    } catch (e) {
      console.error("Upload failed", e);
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const deleteDocument = async (id: string) => {
    try {
      await fetch(`http://localhost:8080/api/v1/documents/${id}`, { method: 'DELETE' });
      await fetchDocuments();
    } catch (e) {
      console.error("Delete failed", e);
    }
  };

  return (
    <div className="sidebar sidebar-container">
      <div className="brand">
        <div className="brand-icon">
          <ZapIcon />
        </div>
        <div className="brand-title">DocuMind AI</div>
      </div>

      <div className="upload-section">
        <input 
          type="file" 
          ref={fileInputRef}
          style={{ display: 'none' }}
          onChange={handleFileSelect}
          accept="application/pdf"
        />
        <div 
          className={`upload-box ${isDragging ? 'dragging' : ''}`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={() => fileInputRef.current?.click()}
        >
          <div className="upload-icon">
            <UploadCloudIcon />
          </div>
          <div className="upload-text">
            {isUploading ? 'Uploading & Processing...' : 'Click or drag PDF to upload'}
          </div>
        </div>
      </div>

      <div className="document-list-title">Knowledge Base</div>
      <div className="document-list">
        {documents.length === 0 && (
          <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', textAlign: 'center', marginTop: '20px' }}>
            No documents uploaded yet.
          </div>
        )}
        
        {documents.map(doc => (
          <div key={doc.id} className="document-item">
            <div className="document-icon">
              <FileTextIcon />
            </div>
            <div className="document-info">
              <div className="document-name" title={doc.originalFilename}>{doc.originalFilename}</div>
              <div className={`document-status ${doc.status.toLowerCase()}`}>
                {doc.status === 'PROCESSED' ? <CheckCircleIcon /> : <ClockIcon />}
                {doc.status}
              </div>
            </div>
            <button className="btn-icon delete-btn" onClick={(e) => { e.stopPropagation(); deleteDocument(doc.id); }}>
              <Trash2Icon />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
