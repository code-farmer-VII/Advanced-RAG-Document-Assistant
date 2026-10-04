"use client";

import React, { useState, useRef, useEffect } from 'react';
import './ChatArea.css';

interface Citation {
  documentId: string;
  documentName: string;
  pageNumber: number;
}

interface Message {
  id: string;
  role: 'user' | 'ai';
  content: string;
  citations?: Citation[];
}

// Inline SVGs replacing lucide-react to avoid NPM corporate proxy block
const BotIcon = () => <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="10" rx="2"></rect><circle cx="12" cy="5" r="2"></circle><path d="M12 7v4"></path><line x1="8" y1="16" x2="8" y2="16"></line><line x1="16" y1="16" x2="16" y2="16"></line></svg>;
const SparklesIcon = () => <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>;
const BookOpenIcon = () => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path></svg>;
const ChevronRightIcon = () => <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>;
const SendIcon = () => <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>;

export default function ChatArea() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, isLoading]);

  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value);
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 150)}px`;
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  const sendMessage = async () => {
    if (!input.trim() || isLoading) return;

    const userMsg: Message = { id: Date.now().toString(), role: 'user', content: input.trim() };
    setMessages(prev => [...prev, userMsg]);
    setInput('');
    setIsLoading(true);

    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }

    try {
      const res = await fetch('http://localhost:8080/api/v1/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          conversationId: conversationId,
          question: userMsg.content
        })
      });

      if (res.ok) {
        const data = await res.json();
        
        if (data.metadata?.conversationId) {
          setConversationId(data.metadata.conversationId);
        }

        const aiMsg: Message = {
          id: (Date.now() + 1).toString(),
          role: 'ai',
          content: data.answer,
          citations: data.citations
        };
        setMessages(prev => [...prev, aiMsg]);
      } else {
        throw new Error("Failed to get response");
      }
    } catch (e) {
      console.error(e);
      // Fallback for demonstration when backend is down
      setTimeout(() => {
        setMessages(prev => [...prev, {
          id: (Date.now() + 1).toString(),
          role: 'ai',
          content: "I'm sorry, I cannot connect to the backend server right now. Please ensure the Spring Boot application is running.",
        }]);
        setIsLoading(false);
      }, 1000);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="chat-container">
      <div className="chat-header">
        <div className="chat-header-title">
          <div style={{ color: 'var(--accent-primary)', display: 'flex' }}>
            <BotIcon />
          </div>
          DocuMind Assistant
        </div>
      </div>

      {messages.length === 0 ? (
        <div className="welcome-screen">
          <div className="welcome-icon">
            <SparklesIcon />
          </div>
          <h1 className="title welcome-title">How can I help you today?</h1>
          <p className="welcome-desc">
            Upload your PDF documents in the sidebar, and I can answer any questions you have about them using advanced AI retrieval.
          </p>
        </div>
      ) : (
        <div className="messages-area">
          {messages.map((msg) => (
            <div key={msg.id} className={`message-wrapper ${msg.role}`}>
              <div className="message-bubble">
                {msg.content}
              </div>
              
              {msg.role === 'ai' && msg.citations && msg.citations.length > 0 && (
                <div className="citations-container">
                  <div className="citations-title">Sources</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                    {msg.citations.map((cite, idx) => (
                      <div key={idx} className="citation-chip">
                        <div className="citation-icon" style={{display: 'flex'}}>
                          <BookOpenIcon />
                        </div>
                        <span>{cite.documentName}</span>
                        {cite.pageNumber && (
                          <>
                            <div style={{ opacity: 0.5, display: 'flex' }}>
                              <ChevronRightIcon />
                            </div>
                            <span>Pg {cite.pageNumber}</span>
                          </>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))}
          
          {isLoading && (
            <div className="message-wrapper ai">
              <div className="message-bubble" style={{ padding: '12px' }}>
                <div className="loading-indicator">
                  <div className="dot"></div>
                  <div className="dot"></div>
                  <div className="dot"></div>
                </div>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>
      )}

      <div className="input-area">
        <div className="input-box">
          <textarea
            ref={textareaRef}
            className="chat-input"
            placeholder="Ask a question about your documents..."
            value={input}
            onChange={handleInput}
            onKeyDown={handleKeyDown}
            rows={1}
          />
          <button 
            className="send-btn" 
            onClick={sendMessage}
            disabled={!input.trim() || isLoading}
          >
            <SendIcon />
          </button>
        </div>
      </div>
    </div>
  );
}
