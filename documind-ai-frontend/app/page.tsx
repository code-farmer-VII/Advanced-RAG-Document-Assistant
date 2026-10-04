import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';

export default function Home() {
  return (
    <main className="app-container">
      <Sidebar />
      <div className="main-content">
        <ChatArea />
      </div>
    </main>
  );
}
